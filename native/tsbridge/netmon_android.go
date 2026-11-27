// Copyright (c) RemoteHub contributors
// SPDX-License-Identifier: BSD-3-Clause

//go:build android

package tsbridge

import (
	"net"

	"github.com/wlynxg/anet"
	"tailscale.com/net/netmon"
)

// Android 11 (API 30) and later deny the netlink route dump that Go's
// net.Interfaces() performs, so tsnet's Start aborts with
//
//	tsnet: route ip+net: netlinkrib: permission denied
//
// anet reads the same information through ioctl(SIOCGIFCONF), which app UIDs are
// still allowed to call, and netmon exposes RegisterInterfaceGetter exactly so a
// host application can supply that implementation. Registering it here means
// every netmon consumer inside tsnet sees working interfaces without netlink.
func init() {
	netmon.RegisterInterfaceGetter(androidInterfaces)
}

func androidInterfaces() ([]netmon.Interface, error) {
	ifs, err := anet.Interfaces()
	if err != nil {
		return nil, err
	}
	out := make([]netmon.Interface, 0, len(ifs))
	for i := range ifs {
		iface := &ifs[i]
		// AltAddrs must be non-nil, even when empty: a nil slice makes netmon fall
		// back to net.Interface.Addrs(), which is the netlink call we are avoiding.
		addrs, err := anet.InterfaceAddrsByInterface(iface)
		if err != nil || addrs == nil {
			addrs = []net.Addr{}
		}
		out = append(out, netmon.Interface{Interface: iface, AltAddrs: addrs})
	}
	return out, nil
}
