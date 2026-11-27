#!/usr/bin/env bash
set -euo pipefail

: "${GH_TOKEN:?}"
: "${GITHUB_REPOSITORY:?}"

API="https://api.github.com/repos/${GITHUB_REPOSITORY}"
mkdir -p composeApp/libs composeApp/src/androidMain/jniLibs/arm64-v8a composeApp/src/androidMain/jniLibs/armeabi-v7a

gh_get() {
  curl -fsS -H "Authorization: Bearer ${GH_TOKEN}" -H "Accept: application/vnd.github+json" "$1"
}

RUNS="$(gh_get "${API}/actions/workflows/native.yml/runs?per_page=20")"

find_artifact() {
  local name="$1"
  for run_id in $(printf '%s' "$RUNS" | python3 -c 'import json,sys; [print(r["id"]) for r in json.load(sys.stdin)["workflow_runs"]]'); do
    local artifacts id
    artifacts="$(gh_get "${API}/actions/runs/${run_id}/artifacts?per_page=100")"
    id="$(printf '%s' "$artifacts" | python3 -c "
import json,sys
data=json.load(sys.stdin)['artifacts']
print(next((str(a['id']) for a in data if a['name']=='$name' and not a['expired']), ''))
")"
    if [ -n "$id" ]; then
      echo "${run_id}:${id}"
      return 0
    fi
  done
  return 1
}

download() {
  local name="$1" dest="$2"
  local found run_id id
  if ! found="$(find_artifact "$name")"; then
    echo "artifact $name not found in recent native runs" >&2
    return 1
  fi
  run_id="${found%%:*}"
  id="${found##*:}"
  mkdir -p "$dest"
  local location
  location="$(curl -sS -o /dev/null -w '%{redirect_url}' \
    -H "Authorization: Bearer ${GH_TOKEN}" -H "Accept: application/vnd.github+json" \
    "${API}/actions/artifacts/${id}/zip")"
  curl -fsSL -o "${dest}/${name}.zip" "$location"
  unzip -o -q "${dest}/${name}.zip" -d "$dest"
  rm "${dest}/${name}.zip"
  echo "downloaded $name from run $run_id"
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
