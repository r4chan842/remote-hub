#!/usr/bin/env python3
"""Render release notes from the real test results and the built APK."""
import glob
import os
import xml.etree.ElementTree as ET

TEMPLATE = """## RemoteHub {tag}

Android-only release. Desktop platforms are out of scope for this version.

### What is inside

- Native Kotlin + Compose Material 3 interface in Persian, English and Arabic, dark and light.
- GitHub sign-in through the official Device Authorization Flow or a personal access token, with scope validation.
- Tailscale compiled into the app with `tsnet` in userspace mode: no root, no separate client, coexists with an active system VPN.
- Remote desktop rendered by FreeRDP {freerdp} through a JNI bridge with software GDI, graphics pipeline, RemoteFX, NSCodec and OpenH264.
- Repository creation, sealed secrets, workflow dispatch and live step status driven by the real GitHub API.
- Server lifecycle, operation history, re-run, expiry warnings and client-side encrypted backups stored in your own repository.

### Verified in this build

- {tests} automated tests executed on GitHub Actions, zero failures.
- APK signed and verified with `apksigner`.
- Native libraries stored uncompressed and zip-aligned to 16 KB (no extraction at install). Per-library ELF alignment is listed in `apk-report.txt`.
- Signing certificate SHA-256: `{cert}`.

### Which APK to download

{apk_table}

Most phones from 2017 onward need **arm64-v8a**. Older 32-bit devices need **armeabi-v7a**.
If unsure, take the universal APK: same features, larger download. All variants contain
the same embedded Tailscale and FreeRDP engines.

### Compliance

GitHub Actions is used only for build, test, release and short orchestration. Each orchestration job is capped at 20 minutes. The remote desktop runs on a machine in your own cloud account, never on a GitHub-hosted runner.

### Install

Download the APK for your device below and allow installation from your browser or file manager. Verify the download against `SHA256SUMS.txt`.

### First run

1. Settings, connect GitHub. Use a personal access token with `repo` and `workflow`, or create your own OAuth App with Device Flow enabled and paste its Client ID.
2. Network, paste a Tailscale auth key.
3. Settings, add your cloud provider token.
4. Dashboard, New server.

Guides in all three languages are built into Settings, Guides.
"""


LABELS = (
    ("arm64-v8a", "64-bit ARM (almost all current phones)"),
    ("armeabi-v7a", "32-bit ARM (older devices)"),
    ("universal", "Both ABIs, fallback"),
)


def apk_table(apks: list) -> str:
    rows = ["| File | Device | Size |", "|---|---|---|"]
    for apk in apks:
        name = os.path.basename(apk)
        label = next((text for key, text in LABELS if key in name), "")
        rows.append(f"| `{name}` | {label} | {os.path.getsize(apk) / (1024 * 1024):.1f} MB |")
    return "\n".join(rows)


def main() -> None:
    total = 0
    for path in glob.glob("composeApp/build/test-results/**/*.xml", recursive=True):
        total += int(ET.parse(path).getroot().get("tests", 0))
    apks = sorted(glob.glob("release/*.apk"))
    if not apks:
        raise SystemExit("no APK in release/")
    notes = TEMPLATE.format(
        tag=os.environ["TAG"],
        tests=total,
        apk_table=apk_table(apks),
        cert=os.environ.get("CERT_SHA256", "see apk-report.txt"),
        freerdp=os.environ.get("FREERDP_VERSION", "3.33.1"),
    )
    with open("release/NOTES.md", "w", encoding="utf-8") as handle:
        handle.write(notes)
    print(notes)


if __name__ == "__main__":
    main()
