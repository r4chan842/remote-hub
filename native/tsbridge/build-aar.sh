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
