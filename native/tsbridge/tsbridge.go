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
	return &Node{
		dir:      stateDir,
		hostname: hostname,
		authKey:  authKey,
		ephem:    ephemeral,
		forwards: map[string]*forwarder{},
	}
}

func (n *Node) appendLog(line string) {
	n.mu.Lock()
	defer n.mu.Unlock()
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
}

func (n *Node) Start() error {
	n.mu.Lock()
	if n.started {
		n.mu.Unlock()
		return nil
	}
	if err := os.MkdirAll(n.dir, 0o700); err != nil {
		n.mu.Unlock()
		return err
	}
	srv := &tsnet.Server{
		Dir:       n.dir,
		Hostname:  n.hostname,
		AuthKey:   n.authKey,
		Ephemeral: n.ephem,
		Logf:      func(format string, args ...any) {},
		UserLogf: func(format string, args ...any) {
			n.appendLog(fmt.Sprintf(format, args...))
		},
	}
	n.srv = srv
	n.started = true
	n.mu.Unlock()

	if err := srv.Start(); err != nil {
		n.mu.Lock()
		n.lastErr = err.Error()
		n.started = false
		n.srv = nil
		n.mu.Unlock()
		return err
	}
	lc, err := srv.LocalClient()
	if err != nil {
		return err
	}
	n.mu.Lock()
	n.lc = lc
	n.mu.Unlock()

	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()
	if _, err := srv.Up(ctx); err != nil {
		n.mu.Lock()
		n.lastErr = err.Error()
		n.mu.Unlock()
		return err
	}
	return nil
}

func (n *Node) StartAsync() {
	go func() {
		if err := n.Start(); err != nil {
			n.appendLog("start failed: " + err.Error())
		}
	}()
}

func (n *Node) StatusJSON() string {
	st := Status{State: "Stopped", Hostname: n.hostname, Userspace: true}
	n.mu.Lock()
	srv, lc := n.srv, n.lc
	st.Error = n.lastErr
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
		return encode(st)
	}
	st.State = s.BackendState
	st.Running = s.BackendState == "Running"
	st.Version = s.Version
	st.Userspace = !s.TUN
	st.Health = s.Health
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

func (n *Node) PeersJSON() string {
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
		return "[]"
	}
	out := make([]Peer, 0, len(s.Peer))
	for _, p := range s.Peer {
		item := Peer{
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
		out = append(out, item)
	}
	return encode(out)
}

func (n *Node) LoginURL() string {
	n.mu.Lock()
	defer n.mu.Unlock()
	return n.authURL
}

func (n *Node) SelfIP() string {
	n.mu.Lock()
	srv := n.srv
	n.mu.Unlock()
	if srv == nil {
		return ""
	}
	ip4, _ := srv.TailscaleIPs()
	return ip4.String()
}

func (n *Node) NotifyNetworkChange() {
	n.mu.Lock()
	srv := n.srv
	n.mu.Unlock()
	if srv == nil {
		return
	}
	sys := srv.Sys()
	if mon := sys.NetMon.Get(); mon != nil {
		mon.InjectEvent()
	}
	if ms := sys.MagicSock.Get(); ms != nil {
		ms.Rebind()
		ms.ReSTUN("android-network-change")
	}
}

func (n *Node) SetNetworkUp(up bool) {
	n.mu.Lock()
	srv := n.srv
	n.mu.Unlock()
	if srv == nil {
		return
	}
	if ms := srv.Sys().MagicSock.Get(); ms != nil {
		ms.SetNetworkUp(up)
	}
}

func (n *Node) Logout() error {
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
	n.mu.Lock()
	srv := n.srv
	fwds := n.forwards
	n.forwards = map[string]*forwarder{}
	n.srv = nil
	n.lc = nil
	n.started = false
	n.mu.Unlock()
	for _, f := range fwds {
		f.cancel()
		f.ln.Close()
	}
	if srv != nil {
		srv.Close()
	}
}

func (n *Node) Wipe() error {
	n.Stop()
	return os.RemoveAll(n.dir)
}

func (n *Node) OpenForward(listenAddr, target string) (string, error) {
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
		return "", err
	}
	ctx, cancel := context.WithCancel(context.Background())
	f := &forwarder{ln: ln, cancel: cancel, target: target}
	n.mu.Lock()
	n.forwards[ln.Addr().String()] = f
	n.mu.Unlock()

	go func() {
		for {
			conn, err := ln.Accept()
			if err != nil {
				return
			}
			go func(c net.Conn) {
				f.mu.Lock()
				f.conns++
				f.mu.Unlock()
				dialCtx, dialCancel := context.WithTimeout(ctx, 20*time.Second)
				upstream, err := srv.Dial(dialCtx, "tcp", target)
				dialCancel()
				if err != nil {
					c.Close()
					return
				}
				pipe(ctx, c, upstream)
			}(conn)
		}
	}()
	return ln.Addr().String(), nil
}

func (n *Node) CloseForward(listenAddr string) {
	n.mu.Lock()
	f, ok := n.forwards[listenAddr]
	delete(n.forwards, listenAddr)
	n.mu.Unlock()
	if ok {
		f.cancel()
		f.ln.Close()
	}
}

func (n *Node) Probe(target string, timeoutMillis int) error {
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
		return err
	}
	return conn.Close()
}

func (n *Node) LatencyMillis(target string, timeoutMillis int) int {
	start := time.Now()
	if err := n.Probe(target, timeoutMillis); err != nil {
		return -1
	}
	return int(time.Since(start).Milliseconds())
}

func (n *Node) AwaitRunning(timeoutMillis int) bool {
	deadline := time.Now().Add(time.Duration(timeoutMillis) * time.Millisecond)
	for time.Now().Before(deadline) {
		n.mu.Lock()
		lc := n.lc
		n.mu.Unlock()
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
	return false
}

func (n *Node) EditPrefs(hostname string) error {
	n.mu.Lock()
	lc := n.lc
	n.mu.Unlock()
	if lc == nil {
		return errors.New("node not started")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	_, err := lc.EditPrefs(ctx, &ipn.MaskedPrefs{
		Prefs:         ipn.Prefs{Hostname: hostname},
		HostnameSet:   true,
	})
	return err
}

func StateDir(base, name string) string {
	return filepath.Join(base, "tsnet-"+name)
}

func pipe(ctx context.Context, a, b net.Conn) {
	done := make(chan struct{}, 2)
	go func() { io.Copy(a, b); done <- struct{}{} }()
	go func() { io.Copy(b, a); done <- struct{}{} }()
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
