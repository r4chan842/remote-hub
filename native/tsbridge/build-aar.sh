#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

: "${ANDROID_HOME:?ANDROID_HOME must point at the Android SDK}"
: "${GOMOBILE_NDK:?GOMOBILE_NDK must point at an NDK root}"

export ANDROID_NDK_HOME="$GOMOBILE_NDK"
export PATH="$PATH:$(go env GOPATH)/bin"

GOMOBILE_VERSION="v0.0.0-20261117202452-47699113848a"

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
test -f "$TSNET_DIR/tsnet/tsnet.go"
mkdir -p .overlay
python3 - "$TSNET_DIR/tsnet/tsnet.go" ".overlay/tsnet.go" <<'PYEOF'
import sys
source, target = sys.argv[1], sys.argv[2]
text = open(source, encoding="utf-8").read()
needle = '\t\tcase "ios", "darwin":\n'
if needle not in text:
    raise SystemExit("tsnet fallback switch not found; upstream changed shape")
text = text.replace(needle, '\t\tcase "ios", "darwin", "android":\n', 1)
open(target, "w", encoding="utf-8").write(text)
print("patched tsnet android executable fallback")
PYEOF
python3 - "$TSNET_DIR/tsnet/tsnet.go" "$PWD/.overlay/tsnet.go" ".overlay/overlay.json" <<'PYEOF'
import json, sys
original, replacement, target = sys.argv[1], sys.argv[2], sys.argv[3]
json.dump({"Replace": {original: replacement}}, open(target, "w"))
print("overlay:", target)
PYEOF
# GOFLAGS only applies to commands that understand -overlay, so go env and the
# module commands above are unaffected.
export GOFLAGS="-overlay=$PWD/.overlay/overlay.json"

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
