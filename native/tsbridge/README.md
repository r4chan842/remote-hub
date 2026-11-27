# tsbridge

Go package compiled into an Android AAR with `gomobile bind`. It embeds `tsnet`
in userspace mode, so the application never requests `VpnService`, never needs
root, and coexists with an active system VPN.

## Surface

| Symbol | Purpose |
| --- | --- |
| `NewNode(dir, hostname, authKey, ephemeral)` | Construct a node bound to a private state directory. |
| `Start` / `StartAsync` | Bring the node up, blocking or background. |
| `StatusJSON` | Backend state, tailnet, IPs, auth URL, health, byte counters. |
| `PeersJSON` | Every peer with address, relay, OS and online flag. |
| `OpenForward` / `CloseForward` | Loopback TCP listener dialled through the tailnet. |
| `Probe` / `LatencyMillis` | Real reachability and round-trip measurement. |
| `NotifyNetworkChange` / `SetNetworkUp` | Android connectivity callbacks, required because netlink is blocked for apps. |
| `Stop` / `Wipe` / `Logout` | Release sockets, drop state, deauthorise. |

## Build

`build-aar.sh` pins the toolchain, verifies the module graph and emits
`tsbridge.aar` plus a sources jar for `arm64-v8a` and `armeabi-v7a`.
