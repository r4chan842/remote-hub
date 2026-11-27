# rdpbridge

JNI bridge between the Kotlin client and FreeRDP 3.33.1. It renders into a
reusable RGBA buffer and copies only into an `AndroidBitmap` that Compose already
owns, so no per-frame allocation happens on the UI thread.

## Design

* `rdpClientContext` is subclassed, so the official client-common layer manages
  channels, auto-reconnect and settings post-processing.
* Software GDI with `PIXEL_FORMAT_BGRA32`; `EndPaint` copies the framebuffer
  under a mutex and raises a dirty flag. The UI thread drains that flag at vsync.
* Graphics pipeline, RemoteFX, NSCodec and OpenH264 are enabled; wallpaper,
  animations, themes and cursor effects are disabled to cut bandwidth.
* Certificate fingerprints are pinned on first use and a changed certificate
  aborts the session instead of silently trusting it.
* Clipboard is wired through `cliprdr` in both directions using the real
  format-list / format-data handshake.
* Resize sends a real `DISP` monitor layout instead of faking a scale factor.

## Build

`build.sh` drives the upstream FreeRDP Android dependency graph (OpenSSL,
OpenH264, uriparser) for one ABI and emits stripped shared objects into
`out/<abi>/`.
