#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

: "${ANDROID_HOME:?ANDROID_HOME must point at the Android SDK}"
: "${GOMOBILE_NDK:?GOMOBILE_NDK must point at an NDK root}"

export ANDROID_NDK_HOME="$GOMOBILE_NDK"
export PATH="$PATH:$(go env GOPATH)/bin"

GOMOBILE_VERSION="v0.0.0-20261117202452-47699113848a"

# anet reads Android interfaces through ioctl instead of the netlink dump that
# Android 11+ denies to app UIDs; netmon_android.go registers it with netmon.
ANET_VERSION="v0.0.5"
go get "github.com/wlynxg/anet@${ANET_VERSION}"
go get "golang.org/x/mobile@${GOMOBILE_VERSION}"
go get -tool "golang.org/x/mobile/cmd/gobind@${GOMOBILE_VERSION}"
go get -tool "golang.org/x/mobile/cmd/gomobile@${GOMOBILE_VERSION}"
go mod tidy
go mod verify

go install "golang.org/x/mobile/cmd/gomobile@${GOMOBILE_VERSION}"
go install "golang.org/x/mobile/cmd/gobind@${GOMOBILE_VERSION}"

gomobile init

# ---------------------------------------------------------------------------
# tsnet on Android: Server.start() calls os.Executable(), which readlinks
# /proc/self/exe. Several Android builds (including recent HyperOS) deny that
# read to app processes, so tsnet aborts with "permission denied" before it ever
# reaches the network. The value is only used to derive a default hostname and
# state directory, and we always pass both, so Android can take the same
# fallback path tsnet already uses for iOS and macOS.
#
# The module cache is read-only, so the one-line change is applied through Go's
# build overlay instead of editing the dependency in place.
# ---------------------------------------------------------------------------
TSNET_DIR="$(go list -m -f '{{.Dir}}' tailscale.com)"
TSNET_FILE="$TSNET_DIR/tsnet/tsnet.go"
test -f "$TSNET_FILE"
# The module cache is extracted read-only; make just this one file writable.
chmod u+w "$TSNET_DIR/tsnet" "$TSNET_FILE"
python3 - "$TSNET_FILE" <<'PYEOF'
import sys
target = sys.argv[1]
text = open(target, encoding="utf-8").read()
patched = '\t\tcase "ios", "darwin", "android":\n'
if patched in text:
    print("tsnet android fallback already present")
else:
    needle = '\t\tcase "ios", "darwin":\n'
    if needle not in text:
        raise SystemExit("tsnet fallback switch not found; upstream changed shape")
    open(target, "w", encoding="utf-8").write(text.replace(needle, patched, 1))
    print("patched tsnet android executable fallback")
PYEOF
grep -n 'case "ios", "darwin", "android"' "$TSNET_FILE"

# Compile the Android-only sources before the long gomobile step so a mistake in
# netmon_android.go surfaces in seconds rather than minutes.
GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build ./...

mkdir -p out
gomobile bind \
  -target=android/arm64,android/arm \
  -androidapi 26 \
  -javapkg app.remotehub \
  -ldflags="-s -w -buildid=" \
  -trimpath \
  -o out/tsbridge.aar \
  ./

ls -l out
