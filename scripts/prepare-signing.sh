#!/usr/bin/env bash
set -euo pipefail

KEYSTORE_PATH="${RUNNER_TEMP:-/tmp}/remotehub-release.jks"

if [ -n "${KEYSTORE_BASE64:-}" ]; then
  printf '%s' "$KEYSTORE_BASE64" | base64 -d > "$KEYSTORE_PATH"
  echo "Using the repository signing key"
else
  : "${KEYSTORE_PASSWORD:=remotehub-ci}"
  : "${KEY_ALIAS:=remotehub}"
  : "${KEY_PASSWORD:=$KEYSTORE_PASSWORD}"
  keytool -genkeypair -v \
    -keystore "$KEYSTORE_PATH" \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 4096 -validity 10950 \
    -dname "CN=RemoteHub, OU=Mobile, O=RemoteHub, L=Unknown, ST=Unknown, C=DE"
  echo "Generated a release key for this build"
fi

keytool -list -keystore "$KEYSTORE_PATH" -storepass "$KEYSTORE_PASSWORD" | head -8

if [ -n "${GITHUB_ENV:-}" ]; then
  {
    echo "RH_KEYSTORE_FILE=$KEYSTORE_PATH"
    echo "RH_KEYSTORE_PASSWORD=$KEYSTORE_PASSWORD"
    echo "RH_KEY_ALIAS=$KEY_ALIAS"
    echo "RH_KEY_PASSWORD=$KEY_PASSWORD"
  } >> "$GITHUB_ENV"
fi
