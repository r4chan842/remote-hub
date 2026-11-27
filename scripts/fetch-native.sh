#!/usr/bin/env bash
set -euo pipefail

: "${GH_TOKEN:?}"
: "${GITHUB_REPOSITORY:?}"

API="https://api.github.com/repos/${GITHUB_REPOSITORY}"
mkdir -p composeApp/libs composeApp/src/androidMain/jniLibs/arm64-v8a composeApp/src/androidMain/jniLibs/armeabi-v7a

latest_run() {
  curl -fsS -H "Authorization: Bearer ${GH_TOKEN}" -H "Accept: application/vnd.github+json" \
    "${API}/actions/workflows/native.yml/runs?status=success&per_page=1" \
    | python3 -c 'import json,sys; runs=json.load(sys.stdin)["workflow_runs"]; print(runs[0]["id"] if runs else "")'
}

RUN_ID="$(latest_run)"
if [ -z "$RUN_ID" ]; then
  echo "No successful Native Engines run found" >&2
  exit 1
fi
echo "Using native run ${RUN_ID}"

ARTIFACTS="$(curl -fsS -H "Authorization: Bearer ${GH_TOKEN}" -H "Accept: application/vnd.github+json" \
  "${API}/actions/runs/${RUN_ID}/artifacts?per_page=100")"

download() {
  local name="$1" dest="$2"
  local id
  id="$(printf '%s' "$ARTIFACTS" | python3 -c "
import json,sys
data=json.load(sys.stdin)['artifacts']
print(next((a['id'] for a in data if a['name']=='$name'), ''))
")"
  if [ -z "$id" ]; then
    echo "artifact $name missing" >&2
    return 1
  fi
  mkdir -p "$dest"
  local location
  location="$(curl -sS -o /dev/null -w '%{redirect_url}' \
    -H "Authorization: Bearer ${GH_TOKEN}" -H "Accept: application/vnd.github+json" \
    "${API}/actions/artifacts/${id}/zip")"
  curl -fsSL -o "${dest}/${name}.zip" "$location"
  unzip -o -q "${dest}/${name}.zip" -d "$dest"
  rm "${dest}/${name}.zip"
  echo "downloaded $name"
}

download tsbridge-aar composeApp/libs
test -f composeApp/libs/tsbridge.aar

download rdpbridge-arm64-v8a composeApp/src/androidMain/jniLibs/arm64-v8a
download rdpbridge-armeabi-v7a composeApp/src/androidMain/jniLibs/armeabi-v7a

test -f composeApp/src/androidMain/jniLibs/arm64-v8a/libremotehub-rdp.so
test -f composeApp/src/androidMain/jniLibs/armeabi-v7a/libremotehub-rdp.so

if [ -f composeApp/libs/go.sum ]; then
  cp composeApp/libs/go.sum native/tsbridge/go.sum
  rm composeApp/libs/go.sum
fi

du -sh composeApp/libs composeApp/src/androidMain/jniLibs/*
