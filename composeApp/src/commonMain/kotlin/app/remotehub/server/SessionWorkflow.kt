package app.remotehub.server

/**
 * The session workflow runs a temporary Windows desktop on a GitHub-hosted runner and
 * joins it to the user's private Tailscale network. The phone reaches it over that
 * tunnel; nothing is exposed on the runner's public address.
 *
 * Hardening compared with the circulating "Windows Cloud RDP" snippet:
 *  - the RDP password is generated on the phone, stored as a sealed repository secret
 *    and masked in the log; it is never printed, so a public repository stays usable
 *  - Network Level Authentication stays enabled
 *  - the RDP firewall rule is scoped to the Tailscale address range, not to every profile
 *  - the node joins as ephemeral, so it disappears from the tailnet when the job ends
 *  - the built-in runneradmin account is left untouched
 *  - the lifetime is the requested number of minutes, capped at the 6 hour job ceiling
 *  - a cleanup step always logs the node out, even when the job is cancelled
 */
object SessionWorkflow {
    const val FILE_NAME = "windows-rdp.yml"
    const val PATH = ".github/workflows/windows-rdp.yml"

    /** GitHub terminates a hosted job after six hours; never ask for more. */
    const val MAX_MINUTES = 360
    const val DEFAULT_MINUTES = 360
    const val MIN_MINUTES = 15

    /** Pinned so a new upstream release cannot silently change the session behaviour. */
    const val TAILSCALE_MSI = "https://pkgs.tailscale.com/stable/tailscale-setup-1.104.7-amd64.msi"

    /** Verified against the published package; the installer is rejected if it differs. */
    const val TAILSCALE_MSI_SHA256 = "676b5b32fdd1aeaee90d8e8f38e222635b216fbdfbf877bf01cb1d7cda330ec5"

    fun render(checkoutSha: String, uploadArtifactSha: String): String = """
name: Windows Cloud RDP

on:
  workflow_dispatch:
    inputs:
      session_id:
        description: Session identifier
        required: true
        type: string
      minutes:
        description: Session lifetime in minutes (15..360)
        required: true
        default: "$DEFAULT_MINUTES"
        type: string

permissions:
  contents: read

concurrency:
  group: remotehub-${'$'}{{ inputs.session_id }}
  cancel-in-progress: false

jobs:
  build:
    name: Windows session
    runs-on: windows-2025
    timeout-minutes: $MAX_MINUTES
    steps:
      - name: 1. Validate request
        shell: pwsh
        env:
          SESSION_ID: ${'$'}{{ inputs.session_id }}
          MINUTES: ${'$'}{{ inputs.minutes }}
        run: |
          ${'$'}ErrorActionPreference = "Stop"
          if (${'$'}env:SESSION_ID -notmatch '^[a-z0-9-]{3,40}${'$'}') { throw "invalid session id" }
          ${'$'}minutes = 0
          if (-not [int]::TryParse(${'$'}env:MINUTES, [ref]${'$'}minutes)) { throw "invalid minutes" }
          if (${'$'}minutes -lt $MIN_MINUTES -or ${'$'}minutes -gt $MAX_MINUTES) { throw "minutes out of range" }
          "RH_SESSION=${'$'}env:SESSION_ID" >> ${'$'}env:GITHUB_ENV
          "RH_MINUTES=${'$'}minutes" >> ${'$'}env:GITHUB_ENV
          "RH_HOSTNAME=rh-${'$'}env:SESSION_ID" >> ${'$'}env:GITHUB_ENV
          # The clock starts now, so setup time is inside the requested lifetime and the
          # job always finishes before GitHub's six hour ceiling.
          "RH_DEADLINE=$((Get-Date).AddMinutes(${'}'}minutes).ToUniversalTime().ToString('o'))" >> ${'$'}env:GITHUB_ENV
          Write-Host "Session ${'$'}env:SESSION_ID requested for ${'$'}minutes minutes."

      - name: 2. Enable Remote Desktop
        shell: pwsh
        run: |
          ${'$'}ErrorActionPreference = "Stop"
          # Accept RDP connections, but keep Network Level Authentication on.
          Set-ItemProperty -Path 'HKLM:\System\CurrentControlSet\Control\Terminal Server' -Name fDenyTSConnections -Value 0
          Set-ItemProperty -Path 'HKLM:\System\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp' -Name UserAuthentication -Value 1
          Set-ItemProperty -Path 'HKLM:\System\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp' -Name SecurityLayer -Value 2
          Set-Service -Name TermService -StartupType Automatic
          Write-Host "Remote Desktop enabled with NLA."

      - name: 3. Restrict RDP to the private network
        shell: pwsh
        run: |
          ${'$'}ErrorActionPreference = "Stop"
          # The runner has a public address. Disable the built-in wide-open rules and
          # allow 3389 only from the Tailscale CGNAT range, so the desktop is reachable
          # from the user's own tailnet and from nowhere else.
          Get-NetFirewallRule -DisplayGroup "Remote Desktop" -ErrorAction SilentlyContinue |
            Disable-NetFirewallRule
          New-NetFirewallRule -DisplayName "RemoteHub RDP over Tailscale" `
            -Direction Inbound -Action Allow -Protocol TCP -LocalPort 3389 `
            -RemoteAddress 100.64.0.0/10 -Profile Any | Out-Null
          New-NetFirewallRule -DisplayName "RemoteHub RDP over Tailscale UDP" `
            -Direction Inbound -Action Allow -Protocol UDP -LocalPort 3389 `
            -RemoteAddress 100.64.0.0/10 -Profile Any | Out-Null
          Write-Host "RDP restricted to 100.64.0.0/10."

      - name: 4. Create the session account
        shell: pwsh
        env:
          RDP_USERNAME: ${'$'}{{ secrets.RDP_USERNAME }}
          RDP_PASSWORD: ${'$'}{{ secrets.RDP_PASSWORD }}
        run: |
          ${'$'}ErrorActionPreference = "Stop"
          # Mask before the value is ever touched, so no later step can leak it.
          Write-Output "::add-mask::${'$'}env:RDP_PASSWORD"
          if ([string]::IsNullOrWhiteSpace(${'$'}env:RDP_PASSWORD)) { throw "RDP_PASSWORD secret is missing" }
          ${'$'}user = ${'$'}env:RDP_USERNAME
          if ([string]::IsNullOrWhiteSpace(${'$'}user)) { ${'$'}user = "rhuser" }
          ${'$'}secure = ConvertTo-SecureString ${'$'}env:RDP_PASSWORD -AsPlainText -Force
          if (Get-LocalUser -Name ${'$'}user -ErrorAction SilentlyContinue) {
            Set-LocalUser -Name ${'$'}user -Password ${'$'}secure
          } else {
            New-LocalUser -Name ${'$'}user -Password ${'$'}secure -PasswordNeverExpires -AccountNeverExpires | Out-Null
          }
          Add-LocalGroupMember -Group "Administrators" -Member ${'$'}user -ErrorAction SilentlyContinue
          Add-LocalGroupMember -Group "Remote Desktop Users" -Member ${'$'}user -ErrorAction SilentlyContinue
          "RH_USER=${'$'}user" >> ${'$'}env:GITHUB_ENV
          Write-Host "Account ${'$'}user is ready. The password stays on your phone."

      - name: 5. Join the private network
        shell: pwsh
        env:
          TAILSCALE_AUTHKEY: ${'$'}{{ secrets.TAILSCALE_AUTHKEY }}
        run: |
          ${'$'}ErrorActionPreference = "Stop"
          Write-Output "::add-mask::${'$'}env:TAILSCALE_AUTHKEY"
          if ([string]::IsNullOrWhiteSpace(${'$'}env:TAILSCALE_AUTHKEY)) { throw "TAILSCALE_AUTHKEY secret is missing" }
          Invoke-WebRequest "$TAILSCALE_MSI" -OutFile "C:\tailscale.msi" -UseBasicParsing
          ${'$'}hash = (Get-FileHash "C:\tailscale.msi" -Algorithm SHA256).Hash.ToLower()
          if (${'$'}hash -ne "$TAILSCALE_MSI_SHA256") { throw "Tailscale installer checksum mismatch" }
          Start-Process msiexec -ArgumentList '/i C:\tailscale.msi /quiet /norestart' -Wait
          Remove-Item C:\tailscale.msi -Force -ErrorAction SilentlyContinue
          ${'$'}exe = "C:\Program Files\Tailscale\tailscale.exe"
          Start-Sleep -Seconds 5
          # Ephemeral: the node is removed from the tailnet as soon as it goes offline.
          & ${'$'}exe up --authkey="${'$'}env:TAILSCALE_AUTHKEY" --hostname="${'$'}env:RH_HOSTNAME" --ephemeral --accept-dns=false --ssh=false --timeout=120s
          if (${'$'}LASTEXITCODE -ne 0) { throw "tailscale up failed" }
          ${'$'}ip = ""
          foreach (${'$'}attempt in 1..20) {
            ${'$'}ip = (& ${'$'}exe ip -4 2>${'$'}null | Select-Object -First 1)
            if (${'$'}ip) { break }
            Start-Sleep -Seconds 3
          }
          if (-not ${'$'}ip) { throw "no Tailscale address assigned" }
          "RH_IP=${'$'}ip" >> ${'$'}env:GITHUB_ENV
          Write-Host "Joined the tailnet as ${'$'}env:RH_HOSTNAME at ${'$'}ip."

      - name: 6. Publish the endpoint
        shell: pwsh
        run: |
          ${'$'}ErrorActionPreference = "Stop"
          # Only the address is published. Credentials never reach the log or the artifact.
          ${'$'}manifest = [ordered]@{
            session_id = ${'$'}env:RH_SESSION
            hostname   = ${'$'}env:RH_HOSTNAME
            address    = ${'$'}env:RH_IP
            port       = 3389
            username   = ${'$'}env:RH_USER
            minutes    = [int]${'$'}env:RH_MINUTES
            started_at = (Get-Date).ToUniversalTime().ToString("o")
          }
          New-Item -ItemType Directory -Force -Path endpoint | Out-Null
          ${'$'}manifest | ConvertTo-Json | Set-Content -Path endpoint/session.json -Encoding UTF8
          ${'$'}summary = "### Windows session ready`n`n| Field | Value |`n| --- | --- |`n"
          ${'$'}summary += "| Address | ``${'$'}env:RH_IP`` |`n"
          ${'$'}summary += "| Port | ``3389`` |`n"
          ${'$'}summary += "| Username | ``${'$'}env:RH_USER`` |`n"
          ${'$'}summary += "| Lifetime | ${'$'}env:RH_MINUTES minutes |`n`n"
          ${'$'}summary += "The password is not shown here. RemoteHub already holds it on your device.`n"
          ${'$'}summary >> ${'$'}env:GITHUB_STEP_SUMMARY
          Write-Host "Endpoint published: ${'$'}env:RH_IP"

      - name: Upload endpoint
        uses: actions/upload-artifact@$uploadArtifactSha
        with:
          name: session-endpoint
          path: endpoint/session.json
          retention-days: 1

      - name: 7. Keep the session alive
        shell: pwsh
        run: |
          ${'$'}ErrorActionPreference = "Stop"
          ${'$'}total = [int]${'$'}env:RH_MINUTES
          ${'$'}deadline = [datetime]::Parse(${'$'}env:RH_DEADLINE).ToUniversalTime()
          Write-Host "Session runs until ${'$'}(${'$'}deadline.ToUniversalTime().ToString('u')) (${'$'}total minutes)."
          ${'$'}minute = 0
          while ((Get-Date).ToUniversalTime() -lt ${'$'}deadline) {
            Start-Sleep -Seconds 60
            ${'$'}minute++
            ${'$'}left = [int]([math]::Ceiling((${'$'}deadline - (Get-Date).ToUniversalTime()).TotalMinutes))
            Write-Host "[${'$'}minute/${'$'}total] alive, ${'$'}left minutes remaining"
          }
          Write-Host "Requested lifetime reached."

      - name: Leave the private network
        if: always()
        shell: pwsh
        run: |
          ${'$'}exe = "C:\Program Files\Tailscale\tailscale.exe"
          if (Test-Path ${'$'}exe) {
            & ${'$'}exe logout 2>${'$'}null
            Write-Host "Node logged out."
          }
          exit 0
""".trimIndent()
}
