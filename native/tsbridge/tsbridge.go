// Copyright (c) RemoteHub contributors
// SPDX-License-Identifier: BSD-3-Clause

// Package tsbridge exposes an embedded Tailscale node (tsnet, userspace) to the
// Android app. Two rules hold everywhere in this file:
//
//  1. No exported call may panic across the JNI boundary. A Go panic there aborts
//     the process inside libgojni.so with no Java stack trace, which is
//     indistinguishable from a random crash for the user. Every entry point is
//     guarded by protect(), which records the stack and returns an error.
//  2. Every failure is recorded with the stage it happened in, so the app can say
//     what broke instead of "connection failed".
package tsbridge

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"tailscale.com/client/local"
	"tailscale.com/ipn"
	"tailscale.com/tsnet"
)

type Node struct {
	mu       sync.Mutex
	srv      *tsnet.Server
	lc       *local.Client
	dir      string
	hostname string
	authKey  string
	ephem    bool
	started  bool
	ready    bool
	stage    string
	lastErr  string
	authURL  string
	logs     []string
	forwards map[string]*forwarder
}

type forwarder struct {
	ln     net.Listener
	cancel context.CancelFunc
	target string
	conns  int64
	mu     sync.Mutex
}

type Status struct {
	State      string   `json:"state"`
	Running    bool     `json:"running"`
	Hostname   string   `json:"hostname"`
	Tailnet    string   `json:"tailnet"`
	IPv4       string   `json:"ipv4"`
	IPv6       string   `json:"ipv6"`
	AuthURL    string   `json:"authUrl"`
	Error      string   `json:"error"`
	Stage      string   `json:"stage"`
	Health     []string `json:"health"`
	PeerCount  int      `json:"peerCount"`
	Online     bool     `json:"online"`
	Relay      string   `json:"relay"`
	RxBytes    int64    `json:"rxBytes"`
	TxBytes    int64    `json:"txBytes"`
	Logs       []string `json:"logs"`
	Version    string   `json:"version"`
	Userspace  bool     `json:"userspace"`
	Forwarders []string `json:"forwarders"`
}

type Peer struct {
	Hostname string   `json:"hostname"`
	DNSName  string   `json:"dnsName"`
	IPv4     string   `json:"ipv4"`
	Online   bool     `json:"online"`
	OS       string   `json:"os"`
	Relay    string   `json:"relay"`
	CurAddr  string   `json:"curAddr"`
	RxBytes  int64    `json:"rxBytes"`
	TxBytes  int64    `json:"txBytes"`
	LastSeen string   `json:"lastSeen"`
	Tags     []string `json:"tags"`
}

func NewNode(stateDir, hostname, authKey string, ephemeral bool) *Node {
	diagLogf("INFO", "node", "new node hostname=%s dir=%s ephemeral=%t keyLength=%d",
		hostname, stateDir, ephemeral, len(authKey))
	return &Node{
		dir:      stateDir,
		hostname: hostname,
		authKey:  authKey,
		ephem:    ephemeral,
		stage:    "created",
		forwards: map[string]*forwarder{},
	}
}

func (n *Node) appendLog(line string) {
	n.mu.Lock()
	n.logs = append(n.logs, line)
	if len(n.logs) > 120 {
		n.logs = n.logs[len(n.logs)-120:]
	}
	if strings.Contains(line, "https://login.tailscale.com/") {
		for _, field := range strings.Fields(line) {
			if strings.HasPrefix(field, "https://login.tailscale.com/") {
				n.authURL = strings.TrimRight(field, ".,;)")
			}
		}
	}
	n.mu.Unlock()
	diagLog("INFO", "tailscale", line)
}

// fail records the stage and the reason, then returns the wrapped error.
func (n *Node) fail(stage string, err error) error {
	wrapped := fmt.Errorf("%s: %w", stage, err)
	n.mu.Lock()
	n.stage = stage
	n.lastErr = wrapped.Error()
	n.mu.Unlock()
	diagLog("ERROR", "node", wrapped.Error())
	return wrapped
}

func (n *Node) setStage(stage string) {
	n.mu.Lock()
	n.stage = stage
	n.mu.Unlock()
	diagLog("INFO", "node", "stage="+stage)
}

func (n *Node) Start() (err error) {
	defer protect("Start", func(message string) {
		n.mu.Lock()
		n.started = false
		n.ready = false
		n.srv = nil
		n.lc = nil
		n.stage = "panic"
		n.lastErr = message
		n.mu.Unlock()
		err = errors.New(message)
	})

	n.mu.Lock()
	if n.started {
		n.mu.Unlock()
		return nil
	}
	dir, hostname, authKey, ephem := n.dir, n.hostname, n.authKey, n.ephem
	n.mu.Unlock()

	n.setStage("storage")
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return n.fail("create state dir", err)
	}
	// Must happen before tsnet touches logpolicy: on Android there is no HOME,
	// no XDG_CACHE_HOME, no writable TMPDIR and the working directory is "/", so
	// logpolicy.LogsDir would panic with "no safe place found to store log state".
	if err := prepareProcessEnv(dir); err != nil {
		return n.fail("prepare writable directories", err)
	}

	n.setStage("tsnet-start")
	startedAt := time.Now()
	srv := &tsnet.Server{
		Dir:       dir,
		Hostname:  hostname,
		AuthKey:   authKey,
		Ephemeral: ephem,
		// The verbose log used to be discarded. It is the only place that says
		// why a handshake, DERP dial or key exchange failed, so keep it.
		Logf: func(format string, args ...any) {
			diagLog("DEBUG", "tsnet", fmt.Sprintf(format, args...))
		},
		UserLogf: func(format string, args ...any) {
			n.appendLog(fmt.Sprintf(format, args...))
		},
	}
	n.mu.Lock()
	n.srv = srv
	n.started = true
	n.mu.Unlock()

	if err := srv.Start(); err != nil {
		n.mu.Lock()
		n.started = false
		n.srv = nil
		n.mu.Unlock()
		return n.fail("tsnet start", err)
	}
	diagLogf("INFO", "node", "tsnet started in %dms", time.Since(startedAt).Milliseconds())

	n.setStage("local-client")
	lc, err := srv.LocalClient()
	if err != nil {
		return n.fail("local client", err)
	}
	n.mu.Lock()
	n.lc = lc
	n.mu.Unlock()

	n.setStage("login")
	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()
	upAt := time.Now()
	if _, err := srv.Up(ctx); err != nil {
		return n.fail("tailnet login", err)
	}
	// Only now is srv.Sys() populated. The Android connectivity callbacks must not
	// touch the server before this point: a failed start leaves Sys() nil and
	// dereferencing it crashes the whole process inside libgojni.
	n.mu.Lock()
	n.ready = true
	n.stage = "running"
	n.lastErr = ""
	n.mu.Unlock()
	diagLogf("INFO", "node", "tailnet login finished in %dms", time.Since(upAt).Milliseconds())
	return nil
}

func (n *Node) StartAsync() {
	goSafe("StartAsync", func() {
		if err := n.Start(); err != nil {
			n.appendLog("start failed: " + err.Error())
		}
	})
}

func (n *Node) StatusJSON() (out string) {
	defer protect("StatusJSON", func(message string) {
		out = encode(Status{
			State:      "Error",
			Hostname:   n.hostname,
			Error:      message,
			Stage:      "status",
			Health:     []string{},
			Logs:       []string{},
			Forwarders: []string{},
		})
	})
	// Never emit JSON null for a list: the Kotlin decoder expects an array.
	st := Status{
		State:      "Stopped",
		Hostname:   n.hostname,
		Userspace:  true,
		Health:     []string{},
		Logs:       []string{},
		Forwarders: []string{},
	}
	n.mu.Lock()
	srv, lc := n.srv, n.lc
	st.Error = n.lastErr
	st.Stage = n.stage
	st.AuthURL = n.authURL
	st.Logs = append([]string{}, n.logs...)
	for addr := range n.forwards {
		st.Forwarders = append(st.Forwarders, addr)
	}
	n.mu.Unlock()

	if srv == nil || lc == nil {
		return encode(st)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()
	s, err := lc.Status(ctx)
	if err != nil {
		st.Error = err.Error()
		diagLog("WARN", "node", "status query: "+err.Error())
		return encode(st)
	}
	st.State = s.BackendState
	st.Running = s.BackendState == "Running"
	st.Version = s.Version
	st.Userspace = !s.TUN
	if len(s.Health) > 0 {
		st.Health = append([]string{}, s.Health...)
		for _, item := range s.Health {
			diagLog("WARN", "health", item)
		}
	}
	st.PeerCount = len(s.Peer)
	if s.CurrentTailnet != nil {
		st.Tailnet = s.CurrentTailnet.Name
	}
	if s.Self != nil {
		st.Hostname = strings.TrimSuffix(s.Self.DNSName, ".")
		st.Online = s.Self.Online
		st.Relay = s.Self.Relay
		st.RxBytes = s.Self.RxBytes
		st.TxBytes = s.Self.TxBytes
	}
	for _, addr := range s.TailscaleIPs {
		if addr.Is4() {
			st.IPv4 = addr.String()
		} else {
			st.IPv6 = addr.String()
		}
	}
	if s.AuthURL != "" {
		st.AuthURL = s.AuthURL
	}
	return encode(st)
}

func (n *Node) PeersJSON() (out string) {
	defer protect("PeersJSON", func(string) { out = "[]" })
	n.mu.Lock()
	lc := n.lc
	n.mu.Unlock()
	if lc == nil {
		return "[]"
	}
	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()
	s, err := lc.Status(ctx)
	if err != nil {
		diagLog("WARN", "node", "peers query: "+err.Error())
		return "[]"
	}
	peers := make([]Peer, 0, len(s.Peer))
	for _, p := range s.Peer {
		item := Peer{
			Tags:     []string{},
			Hostname: p.HostName,
			DNSName:  strings.TrimSuffix(p.DNSName, "."),
			Online:   p.Online,
			OS:       p.OS,
			Relay:    p.Relay,
			CurAddr:  p.CurAddr,
			RxBytes:  p.RxBytes,
			TxBytes:  p.TxBytes,
		}
		for _, addr := range p.TailscaleIPs {
			if addr.Is4() {
				item.IPv4 = addr.String()
			}
		}
		if p.Tags != nil {
			item.Tags = p.Tags.AsSlice()
		}
		if !p.LastSeen.IsZero() {
			item.LastSeen = p.LastSeen.UTC().Format(time.RFC3339)
		}
		peers = append(peers, item)
	}
	return encode(peers)
}

func (n *Node) LoginURL() (url string) {
	defer protect("LoginURL", func(string) { url = "" })
	n.mu.Lock()
	defer n.mu.Unlock()
	return n.authURL
}

func (n *Node) SelfIP() (ip string) {
	defer protect("SelfIP", func(string) { ip = "" })
	n.mu.Lock()
	srv, ready := n.srv, n.ready
	n.mu.Unlock()
	if srv == nil || !ready {
		return ""
	}
	ip4, _ := srv.TailscaleIPs()
	return ip4.String()
}

func (n *Node) NotifyNetworkChange() {
	defer protect("NotifyNetworkChange", nil)
	n.mu.Lock()
	srv, ready := n.srv, n.ready
	n.mu.Unlock()
	if srv == nil || !ready {
		return
	}
	sys := srv.Sys()
	if sys == nil {
		return
	}
	if mon := sys.NetMon.Get(); mon != nil {
		mon.InjectEvent()
	}
	if ms := sys.MagicSock.Get(); ms != nil {
		ms.Rebind()
		ms.ReSTUN("android-network-change")
	}
}

func (n *Node) SetNetworkUp(up bool) {
	defer protect("SetNetworkUp", nil)
	n.mu.Lock()
	srv, ready := n.srv, n.ready
	n.mu.Unlock()
	if srv == nil || !ready {
		return
	}
	sys := srv.Sys()
	if sys == nil {
		return
	}
	if ms := sys.MagicSock.Get(); ms != nil {
		ms.SetNetworkUp(up)
	}
}

func (n *Node) Logout() (err error) {
	defer protect("Logout", func(message string) { err = errors.New(message) })
	n.mu.Lock()
	lc := n.lc
	n.mu.Unlock()
	if lc == nil {
		return errors.New("node not started")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	return lc.Logout(ctx)
}

func (n *Node) Stop() {
	defer protect("Stop", nil)
	n.mu.Lock()
	srv := n.srv
	fwds := n.forwards
	n.forwards = map[string]*forwarder{}
	n.srv = nil
	n.lc = nil
	n.started = false
	n.ready = false
	n.stage = "stopped"
	n.mu.Unlock()
	diagLog("INFO", "node", "stopping node")
	for _, f := range fwds {
		f.cancel()
		f.ln.Close()
	}
	if srv != nil {
		srv.Close()
	}
}

func (n *Node) Wipe() (err error) {
	defer protect("Wipe", func(message string) { err = errors.New(message) })
	n.Stop()
	return os.RemoveAll(n.dir)
}

func (n *Node) OpenForward(listenAddr, target string) (addr string, err error) {
	defer protect("OpenForward", func(message string) { addr, err = "", errors.New(message) })
	n.mu.Lock()
	srv := n.srv
	if srv == nil {
		n.mu.Unlock()
		return "", errors.New("node not started")
	}
	if existing, ok := n.forwards[listenAddr]; ok {
		n.mu.Unlock()
		return existing.target, nil
	}
	n.mu.Unlock()

	ln, err := net.Listen("tcp", listenAddr)
	if err != nil {
		diagLog("ERROR", "forward", "listen "+listenAddr+": "+err.Error())
		return "", err
	}
	ctx, cancel := context.WithCancel(context.Background())
	f := &forwarder{ln: ln, cancel: cancel, target: target}
	bound := ln.Addr().String()
	n.mu.Lock()
	n.forwards[bound] = f
	n.mu.Unlock()
	diagLogf("INFO", "forward", "listening on %s -> %s", bound, target)

	goSafe("forward-accept", func() {
		for {
			conn, err := ln.Accept()
			if err != nil {
				return
			}
			goSafe("forward-conn", func() {
				f.mu.Lock()
				f.conns++
				f.mu.Unlock()
				dialCtx, dialCancel := context.WithTimeout(ctx, 20*time.Second)
				upstream, err := srv.Dial(dialCtx, "tcp", target)
				dialCancel()
				if err != nil {
					diagLog("WARN", "forward", "dial "+target+": "+err.Error())
					conn.Close()
					return
				}
				pipe(ctx, conn, upstream)
			})
		}
	})
	return bound, nil
}

func (n *Node) CloseForward(listenAddr string) {
	defer protect("CloseForward", nil)
	n.mu.Lock()
	f, ok := n.forwards[listenAddr]
	delete(n.forwards, listenAddr)
	n.mu.Unlock()
	if ok {
		f.cancel()
		f.ln.Close()
		diagLog("INFO", "forward", "closed "+listenAddr)
	}
}

func (n *Node) Probe(target string, timeoutMillis int) (err error) {
	defer protect("Probe", func(message string) { err = errors.New(message) })
	n.mu.Lock()
	srv := n.srv
	n.mu.Unlock()
	if srv == nil {
		return errors.New("node not started")
	}
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeoutMillis)*time.Millisecond)
	defer cancel()
	conn, err := srv.Dial(ctx, "tcp", target)
	if err != nil {
		diagLog("WARN", "probe", target+": "+err.Error())
		return err
	}
	return conn.Close()
}

func (n *Node) LatencyMillis(target string, timeoutMillis int) (millis int) {
	defer protect("LatencyMillis", func(string) { millis = -1 })
	start := time.Now()
	if err := n.Probe(target, timeoutMillis); err != nil {
		return -1
	}
	return int(time.Since(start).Milliseconds())
}

func (n *Node) AwaitRunning(timeoutMillis int) (up bool) {
	defer protect("AwaitRunning", func(string) { up = false })
	deadline := time.Now().Add(time.Duration(timeoutMillis) * time.Millisecond)
	for time.Now().Before(deadline) {
		n.mu.Lock()
		lc, stage, lastErr := n.lc, n.stage, n.lastErr
		n.mu.Unlock()
		if stage == "panic" {
			diagLog("ERROR", "node", "start aborted: "+lastErr)
			return false
		}
		if lc != nil {
			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			s, err := lc.StatusWithoutPeers(ctx)
			cancel()
			if err == nil && s.BackendState == "Running" {
				return true
			}
		}
		time.Sleep(700 * time.Millisecond)
	}
	n.mu.Lock()
	stage := n.stage
	n.mu.Unlock()
	diagLogf("WARN", "node", "timed out after %dms at stage=%s", timeoutMillis, stage)
	return false
}

func (n *Node) EditPrefs(hostname string) (err error) {
	defer protect("EditPrefs", func(message string) { err = errors.New(message) })
	n.mu.Lock()
	lc := n.lc
	n.mu.Unlock()
	if lc == nil {
		return errors.New("node not started")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	_, err = lc.EditPrefs(ctx, &ipn.MaskedPrefs{
		Prefs:       ipn.Prefs{Hostname: hostname},
		HostnameSet: true,
	})
	return err
}

func StateDir(base, name string) string {
	return filepath.Join(base, "tsnet-"+name)
}

func pipe(ctx context.Context, a, b net.Conn) {
	done := make(chan struct{}, 2)
	goSafe("pipe-a", func() { io.Copy(a, b); done <- struct{}{} })
	goSafe("pipe-b", func() { io.Copy(b, a); done <- struct{}{} })
	select {
	case <-done:
	case <-ctx.Done():
	}
	a.Close()
	b.Close()
}

func encode(v any) string {
	raw, err := json.Marshal(v)
	if err != nil {
		return "{}"
	}
	return string(raw)
}
