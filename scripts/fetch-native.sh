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
# The artifact preserves the producer's relative path, so flatten it.
AAR="$(find composeApp/libs -name 'tsbridge.aar' | head -1)"
test -n "$AAR"
if [ "$AAR" != "composeApp/libs/tsbridge.aar" ]; then
  mv "$AAR" composeApp/libs/tsbridge.aar
fi
GO_SUM="$(find composeApp/libs -name 'go.sum' | head -1)"
if [ -n "$GO_SUM" ]; then
  cp "$GO_SUM" native/tsbridge/go.sum
fi
find composeApp/libs -mindepth 1 -type d -exec rm -rf {} +

for ABI in arm64-v8a armeabi-v7a; do
  DEST="composeApp/src/androidMain/jniLibs/${ABI}"
  download "rdpbridge-${ABI}" "$DEST"
  while IFS= read -r lib; do
    base="$(basename "$lib")"
    if [ "$lib" != "${DEST}/${base}" ]; then
      mv "$lib" "${DEST}/${base}"
    fi
  done < <(find "$DEST" -name '*.so')
  find "$DEST" -mindepth 1 -type d -exec rm -rf {} +
  test -f "${DEST}/libremotehub-rdp.so"
done

du -sh composeApp/libs composeApp/src/androidMain/jniLibs/*
