# RemoteHub

Cross-platform remote server manager for **Android** and **Windows**, built with **Kotlin** and **Compose Multiplatform**, using the official **Material 3** component set and official **Material Symbols**.

No WebView UI. No HTML screens. Every screen in this repository is real Compose code, and every screenshot is rendered from that code in GitHub Actions.

## Status

Phase 1 — design system, navigation and all primary screens implemented in Compose Material 3, verified and rendered in CI.

| Area | State |
|---|---|
| Design system (M3 tokens, typography, shapes, status colors) | implemented |
| Material Symbols (official font, pinned upstream commit) | implemented |
| Localization: فارسی / English / العربية + RTL | implemented |
| Navigation (NavigationBar compact, NavigationRail expanded) | implemented |
| Dashboard, GitHub auth, network, create wizard, server detail, remote desktop, settings | implemented |
| Unit tests | run in GitHub Actions |
| Screen renders (44 PNGs) | produced in GitHub Actions |
| GitHub infrastructure module | next phase |
| Go `tsnet` network core | next phase |
| FreeRDP remote desktop engine | next phase |
| Android target and Windows packaging | next phase |

## Build and verify

Everything runs in GitHub Actions. Nothing needs to be installed locally to review the design.

```
gradle :composeApp:desktopTest
gradle :composeApp:renderScreens
```

`renderScreens` renders each screen at phone and desktop sizes, in light and dark themes, in Persian (RTL) and English (LTR), and writes PNG files to `composeApp/build/screens`.

## GitHub sign-in

- Use an existing GitHub account and authorize Device Flow in your browser, or use a personal access token with access to your own repository.
- To ship a build with Device Flow preconfigured on Android, enable Device Flow in your GitHub OAuth App and set the repository **variable** `RH_GITHUB_CLIENT_ID` to the public Client ID. Alternatively pass `-PgithubClientId=...` when building locally.
- If no Client ID was bundled, enter one in the Device Flow tab or use the personal token tab. The Windows app also accepts the `RH_GITHUB_CLIENT_ID` environment variable. Never enter an OAuth client secret in the app.

## Repository layout

```
composeApp/src/commonMain/kotlin/app/remotehub/
  design/      Material 3 theme, Material Symbols, shared components
  domain/      Pure domain models, no platform or UI dependencies
  i18n/        Typed string tables per locale
  ui/          Screens and navigation shell
composeApp/src/desktopMain/kotlin/app/remotehub/
  Main.kt              Desktop application entry point
  tools/RenderScreens.kt  Deterministic screen renderer used by CI
docs/          Architecture, design system, compliance
```

## Compliance

GitHub Actions is used only for building, testing and releasing this software project. Remote desktop infrastructure runs on a provider that permits remote access. See `docs/COMPLIANCE.md`.

## Assets

Material Symbols and Vazirmatn are vendored at pinned upstream commits. See `assets/PINNED.txt`.
