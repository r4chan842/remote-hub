// Copyright (c) RemoteHub contributors
// SPDX-License-Identifier: BSD-3-Clause

package tsbridge

import (
	"errors"
	"fmt"

	"github.com/tailscale/wireguard-go/tun"
)

// UseTun hands the file descriptor of an Android VpnService interface to tsnet.
//
// It must be called before Start: tsnet reads Server.Tun once, while building the
// engine. With a real device attached, every app on the phone can reach the
// tailnet, because Android routes 100.64.0.0/10 into this interface. RemoteHub's
// own sockets stay outside the tunnel (the VpnService excludes this package), so
// the app's in-process RDP forwarder keeps working through netstack.
//
// CreateUnmonitoredTUNFromFD is the same constructor the official Tailscale
// Android client uses: it takes ownership of the descriptor, puts it in
// non-blocking mode and reads the interface name back with ioctl, without asking
// netlink for a route dump (which Android denies to app UIDs).
func (n *Node) UseTun(fd int) (err error) {
	defer protect("UseTun", func(message string) { err = errors.New(message) })
	if fd < 0 {
		return errors.New("invalid tun descriptor")
	}
	n.mu.Lock()
	if n.started {
		n.mu.Unlock()
		return errors.New("tun must be attached before the node starts")
	}
	if n.tunDev != nil {
		n.mu.Unlock()
		return errors.New("a tun device is already attached")
	}
	n.mu.Unlock()

	device, name, err := tun.CreateUnmonitoredTUNFromFD(fd)
	if err != nil {
		diagLogf("ERROR", "tun", "cannot adopt fd %d: %v", fd, err)
		return fmt.Errorf("adopt tun fd %d: %w", fd, err)
	}
	n.mu.Lock()
	n.tunDev = device
	n.tunName = name
	n.mu.Unlock()
	diagLogf("INFO", "tun", "device-wide tunnel attached interface=%s fd=%d mtu=%d", name, fd, deviceMTU(device))
	return nil
}

// TunName reports the interface Android created, for the diagnostics screen.
func (n *Node) TunName() (name string) {
	defer protect("TunName", func(string) { name = "" })
	n.mu.Lock()
	defer n.mu.Unlock()
	return n.tunName
}

func deviceMTU(device tun.Device) int {
	mtu, err := device.MTU()
	if err != nil {
		return 0
	}
	return mtu
}

// closeTun releases the interface after tsnet has stopped using it. The descriptor
// belongs to the VpnService, so closing it here also tells Android the tunnel is
// gone; the service stops itself right after.
func (n *Node) closeTun() {
	n.mu.Lock()
	device := n.tunDev
	n.tunDev = nil
	n.tunName = ""
	n.mu.Unlock()
	if device == nil {
		return
	}
	if err := device.Close(); err != nil {
		diagLogf("WARN", "tun", "closing tun device: %v", err)
	}
	diagLog("INFO", "tun", "device-wide tunnel closed")
}
