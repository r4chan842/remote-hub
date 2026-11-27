package app.remotehub.server

object SessionWorkflow {
    const val FILE_NAME = "session.yml"
    const val PATH = ".github/workflows/session.yml"

    fun render(checkoutSha: String, uploadArtifactSha: String): String = """
name: Provision Remote Host

on:
  workflow_dispatch:
    inputs:
      session_id:
        description: Session identifier
        required: true
        type: string
      action:
        description: provision or destroy
        required: true
        default: provision
        type: string
      minutes:
        description: Requested lifetime, enforced on the host itself
        required: true
        default: "120"
        type: string

permissions:
  contents: read

concurrency:
  group: host-${'$'}{{ inputs.session_id }}
  cancel-in-progress: false

jobs:
  orchestrate:
    name: Orchestrate provider
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@$checkoutSha

      - name: Validate request
        env:
          ACTION: ${'$'}{{ inputs.action }}
          MINUTES: ${'$'}{{ inputs.minutes }}
          PROVIDER: ${'$'}{{ vars.REMOTEHUB_PROVIDER }}
        run: |
          set -euo pipefail
          case "${'$'}ACTION" in provision|destroy) ;; *) echo "bad action" >&2; exit 1 ;; esac
          printf '%s' "${'$'}MINUTES" | grep -Eq '^[0-9]+$' || { echo "bad minutes" >&2; exit 1; }
          [ "${'$'}MINUTES" -ge 15 ] && [ "${'$'}MINUTES" -le 1440 ] || { echo "minutes out of range" >&2; exit 1; }
          case "${'$'}PROVIDER" in hetzner|digitalocean) ;; *) echo "unsupported provider" >&2; exit 1 ;; esac
          {
            echo "RH_ACTION=${'$'}ACTION"
            echo "RH_MINUTES=${'$'}MINUTES"
            echo "RH_PROVIDER=${'$'}PROVIDER"
          } >> "${'$'}GITHUB_ENV"

      - name: Provision host
        if: env.RH_ACTION == 'provision'
        env:
          SESSION_ID: ${'$'}{{ inputs.session_id }}
          PROVIDER_TOKEN: ${'$'}{{ secrets.PROVIDER_TOKEN }}
          TS_AUTHKEY: ${'$'}{{ secrets.TS_AUTHKEY }}
          RDP_USERNAME: ${'$'}{{ secrets.RDP_USERNAME }}
          RDP_PASSWORD: ${'$'}{{ secrets.RDP_PASSWORD }}
          REGION: ${'$'}{{ secrets.SESSION_REGION }}
          SIZE: ${'$'}{{ secrets.SESSION_SIZE }}
          IMAGE: ${'$'}{{ secrets.SESSION_IMAGE }}
        run: bash scripts/provision-host.sh provision

      - name: Destroy host
        if: env.RH_ACTION == 'destroy'
        env:
          SESSION_ID: ${'$'}{{ inputs.session_id }}
          PROVIDER_TOKEN: ${'$'}{{ secrets.PROVIDER_TOKEN }}
        run: bash scripts/provision-host.sh destroy

      - name: Redact and upload manifest
        if: always()
        run: |
          set -euo pipefail
          if [ -d manifest ]; then
            for f in manifest/*; do
              [ -e "${'$'}f" ] || continue
              sed -Ei 's/(tskey-[a-z]+-)[A-Za-z0-9-]+/\1REDACTED/g' "${'$'}f"
              sed -Ei 's/("(password|token|secret)"[[:space:]]*:[[:space:]]*")[^"]*/\1REDACTED/gi' "${'$'}f"
            done
          fi

      - name: Upload manifest
        if: always()
        uses: actions/upload-artifact@$uploadArtifactSha
        with:
          name: host-manifest
          path: manifest
          retention-days: 1
          if-no-files-found: warn
""".trimIndent()

    const val HOST_SCRIPT_PATH = "scripts/provision-host.sh"

    val hostScript: String = """
#!/usr/bin/env bash
set -euo pipefail

MODE="${'$'}{1:-provision}"
: "${'$'}{SESSION_ID:?}"
: "${'$'}{PROVIDER_TOKEN:?}"
: "${'$'}{RH_PROVIDER:?}"

mkdir -p manifest
LABEL="remotehub=${'$'}SESSION_ID"
HOSTNAME="rh-${'$'}SESSION_ID"

redact() {
  sed -E 's/(tskey-[a-z]+-)[A-Za-z0-9-]+/\1REDACTED/g'
}

hetzner_api() {
  local method="${'$'}1" path="${'$'}2" body="${'$'}3"
  if [ -n "${'$'}body" ]; then
    curl -fsS -X "${'$'}method" "https://api.hetzner.cloud/v1${'$'}path" \
      -H "Authorization: Bearer ${'$'}PROVIDER_TOKEN" \
      -H 'Content-Type: application/json' -d "${'$'}body"
  else
    curl -fsS -X "${'$'}method" "https://api.hetzner.cloud/v1${'$'}path" \
      -H "Authorization: Bearer ${'$'}PROVIDER_TOKEN"
  fi
}

do_api() {
  local method="${'$'}1" path="${'$'}2" body="${'$'}3"
  if [ -n "${'$'}body" ]; then
    curl -fsS -X "${'$'}method" "https://api.digitalocean.com/v2${'$'}path" \
      -H "Authorization: Bearer ${'$'}PROVIDER_TOKEN" \
      -H 'Content-Type: application/json' -d "${'$'}body"
  else
    curl -fsS -X "${'$'}method" "https://api.digitalocean.com/v2${'$'}path" \
      -H "Authorization: Bearer ${'$'}PROVIDER_TOKEN"
  fi
}

cloud_init() {
  cat <<CLOUDINIT
#cloud-config
package_update: true
packages:
  - xrdp
  - xorgxrdp
  - xfce4
  - xfce4-terminal
  - dbus-x11
  - curl
  - jq
  - unattended-upgrades
runcmd:
  - [ bash, -lc, "useradd -m -s /bin/bash ${'$'}RDP_USERNAME || true" ]
  - [ bash, -lc, "printf '%s:%s\\n' '${'$'}RDP_USERNAME' '${'$'}RDP_PASSWORD' | chpasswd" ]
  - [ bash, -lc, "usermod -aG ssl-cert ${'$'}RDP_USERNAME" ]
  - [ bash, -lc, "printf 'xfce4-session\\n' > /home/${'$'}RDP_USERNAME/.xsession && chown ${'$'}RDP_USERNAME:${'$'}RDP_USERNAME /home/${'$'}RDP_USERNAME/.xsession" ]
  - [ bash, -lc, "sed -i 's/^security_layer=.*/security_layer=negotiate/;s/^crypt_level=.*/crypt_level=high/;s/^max_bpp=.*/max_bpp=24/;s/^tcp_nodelay=.*/tcp_nodelay=true/;s/^tcp_keepalive=.*/tcp_keepalive=true/' /etc/xrdp/xrdp.ini" ]
  - [ bash, -lc, "curl -fsSL https://tailscale.com/install.sh | sh" ]
  - [ bash, -lc, "tailscale up --authkey='${'$'}TS_AUTHKEY' --hostname='${'$'}HOSTNAME' --accept-dns=false --ssh=false --timeout=120s" ]
  - [ bash, -lc, "systemctl enable --now xrdp" ]
  - [ bash, -lc, "ufw allow in on tailscale0 to any port 3389 proto tcp || true" ]
  - [ bash, -lc, "printf '#!/usr/bin/env bash\\nsleep %s\\ntailscale logout\\nsystemctl stop xrdp\\nshutdown -h now\\n' ${'$'}(( RH_MINUTES * 60 )) > /usr/local/bin/remotehub-expire && chmod +x /usr/local/bin/remotehub-expire" ]
  - [ bash, -lc, "systemd-run --on-active=${'$'}(( RH_MINUTES * 60 )) --unit=remotehub-expire /usr/local/bin/remotehub-expire" ]
CLOUDINIT
}

provision_hetzner() {
  local userdata
  userdata="${'$'}(cloud_init)"
  local payload
  payload="${'$'}(jq -n --arg name "${'$'}HOSTNAME" --arg type "${'$'}SIZE" --arg image "${'$'}IMAGE" \
    --arg loc "${'$'}REGION" --arg ud "${'$'}userdata" --arg label "${'$'}LABEL" \
    '{name:${'$'}name, server_type:${'$'}type, image:${'$'}image, location:${'$'}loc, user_data:${'$'}ud, labels:{remotehub:${'$'}label}, start_after_create:true}')"
  local created
  created="${'$'}(hetzner_api POST /servers "${'$'}payload")"
  local id ipv4
  id="${'$'}(printf '%s' "${'$'}created" | jq -r '.server.id')"
  for _ in ${'$'}(seq 1 60); do
    local detail
    detail="${'$'}(hetzner_api GET "/servers/${'$'}id" "")"
    local status
    status="${'$'}(printf '%s' "${'$'}detail" | jq -r '.server.status')"
    ipv4="${'$'}(printf '%s' "${'$'}detail" | jq -r '.server.public_net.ipv4.ip')"
    if [ "${'$'}status" = "running" ] && [ "${'$'}ipv4" != "null" ]; then break; fi
    sleep 5
  done
  jq -n --arg provider hetzner --arg id "${'$'}id" --arg ipv4 "${'$'}ipv4" \
    --arg hostname "${'$'}HOSTNAME" --arg session "${'$'}SESSION_ID" \
    --arg user "${'$'}RDP_USERNAME" --argjson port 3389 --argjson minutes "${'$'}RH_MINUTES" \
    --arg created "${'$'}(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{provider:${'$'}provider, provider_id:${'$'}id, public_ipv4:${'$'}ipv4, hostname:${'$'}hostname, session_id:${'$'}session, username:${'$'}user, port:${'$'}port, minutes:${'$'}minutes, created_at:${'$'}created}' \
    > manifest/host.json
}

destroy_hetzner() {
  local list id
  list="${'$'}(hetzner_api GET "/servers?label_selector=${'$'}LABEL" "")"
  id="${'$'}(printf '%s' "${'$'}list" | jq -r '.servers[0].id // empty')"
  if [ -z "${'$'}id" ]; then
    list="${'$'}(hetzner_api GET "/servers?name=${'$'}HOSTNAME" "")"
    id="${'$'}(printf '%s' "${'$'}list" | jq -r '.servers[0].id // empty')"
  fi
  if [ -n "${'$'}id" ]; then
    hetzner_api DELETE "/servers/${'$'}id" "" >/dev/null
    jq -n --arg provider hetzner --arg id "${'$'}id" '{provider:${'$'}provider, provider_id:${'$'}id, destroyed:true}' > manifest/host.json
  else
    jq -n --arg provider hetzner '{provider:${'$'}provider, destroyed:false, reason:"not found"}' > manifest/host.json
  fi
}

provision_do() {
  local userdata payload created id ipv4
  userdata="${'$'}(cloud_init)"
  payload="${'$'}(jq -n --arg name "${'$'}HOSTNAME" --arg region "${'$'}REGION" --arg size "${'$'}SIZE" \
    --arg image "${'$'}IMAGE" --arg ud "${'$'}userdata" --arg tag "remotehub-${'$'}SESSION_ID" \
    '{name:${'$'}name, region:${'$'}region, size:${'$'}size, image:${'$'}image, user_data:${'$'}ud, tags:[${'$'}tag], backups:false, monitoring:false}')"
  created="${'$'}(do_api POST /droplets "${'$'}payload")"
  id="${'$'}(printf '%s' "${'$'}created" | jq -r '.droplet.id')"
  for _ in ${'$'}(seq 1 60); do
    local detail status
    detail="${'$'}(do_api GET "/droplets/${'$'}id" "")"
    status="${'$'}(printf '%s' "${'$'}detail" | jq -r '.droplet.status')"
    ipv4="${'$'}(printf '%s' "${'$'}detail" | jq -r '[.droplet.networks.v4[] | select(.type=="public")][0].ip_address // empty')"
    if [ "${'$'}status" = "active" ] && [ -n "${'$'}ipv4" ]; then break; fi
    sleep 5
  done
  jq -n --arg provider digitalocean --arg id "${'$'}id" --arg ipv4 "${'$'}ipv4" \
    --arg hostname "${'$'}HOSTNAME" --arg session "${'$'}SESSION_ID" \
    --arg user "${'$'}RDP_USERNAME" --argjson port 3389 --argjson minutes "${'$'}RH_MINUTES" \
    --arg created "${'$'}(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{provider:${'$'}provider, provider_id:${'$'}id, public_ipv4:${'$'}ipv4, hostname:${'$'}hostname, session_id:${'$'}session, username:${'$'}user, port:${'$'}port, minutes:${'$'}minutes, created_at:${'$'}created}' \
    > manifest/host.json
}

destroy_do() {
  local id
  id="${'$'}(do_api GET "/droplets?tag_name=remotehub-${'$'}SESSION_ID" "" | jq -r '.droplets[0].id // empty')"
  if [ -n "${'$'}id" ]; then
    do_api DELETE "/droplets/${'$'}id" "" >/dev/null
    jq -n --arg provider digitalocean --arg id "${'$'}id" '{provider:${'$'}provider, provider_id:${'$'}id, destroyed:true}' > manifest/host.json
  else
    jq -n --arg provider digitalocean '{provider:${'$'}provider, destroyed:false, reason:"not found"}' > manifest/host.json
  fi
}

case "${'$'}RH_PROVIDER:${'$'}MODE" in
  hetzner:provision) provision_hetzner ;;
  hetzner:destroy) destroy_hetzner ;;
  digitalocean:provision) provision_do ;;
  digitalocean:destroy) destroy_do ;;
  *) echo "unsupported combination" >&2; exit 1 ;;
esac

cat manifest/host.json | redact
""".trimIndent()
}
