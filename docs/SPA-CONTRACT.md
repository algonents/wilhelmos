# WilhelmOS SPA Application Contract (v1)

The interface between the WilhelmOS platform and the single-page web
application an integrator runs on the SPA profile (DESIGN.md §10;
engine details in [WEBKIT.md](WEBKIT.md)). Companion to
[KIOSK-CONTRACT.md](KIOSK-CONTRACT.md) — same certification framing
(the layer boundary is the ED-109A §12.4 COTS boundary), different
payload: the application is **web content**, not an executable. The
browser (Cog/WPE WebKit) is platform machinery.

## The two deployment models

**URL is the contract primitive.** The platform loads exactly one URL
fullscreen; where that URL points defines the model:

1. **URL model (primary).** The SPA lives on a server; the device is a
   universal web terminal. Set `WILHELMOS_SPA_URL` (build-time kas
   variable, or the operator tool below) and ship an image with
   `SPA_APP = ""` — zero application content in the image. The app
   deploys, versions and updates entirely server-side. *CM implication
   (documented deliberately): the application version leaves the
   equipment baseline; the image baseline covers the terminal, frontend
   configuration management lives with the server.*
2. **Bundled model.** The SPA is a static bundle baked into the image
   as a package — offline-capable, and the §6 monolithic story (one
   artifact, one SBOM, one baseline) holds exactly as for the native
   kiosk. The out-of-box default bundles the reference SPA so every
   image boots to something visible.

The build fails if neither is configured (image check).

## URL configuration layers

| Layer | Who | Mechanism |
|---|---|---|
| Build time | Integrator | `WILHELMOS_SPA_URL` in kas/image config → baked systemd drop-in |
| Runtime | Operator (tty2) | `sudo wilhelmos-spa-url set <url>` / `show` / `clear` — `/etc` drop-in overrides the baked one, session restarts |
| Future | Operator | The planned tty2 diagnostics TUI, backed by the same tool |

## Packaging obligations (bundled model)

The bundle recipe inherits `spa-app.bbclass` and names the web root its
`do_install` populated:

```bitbake
inherit spa-app
SPA_APP_WEBROOT = "${datadir}/my-spa"
```

The class claims the role (`virtual/spa-app` / `RPROVIDES spa-app` —
exactly one provider per image), installs the stable web root
`/usr/share/spa-app` (relative symlink), and fails `do_install` if
`index.html` is missing. The image selects the provider via `SPA_APP`
and re-checks the web root at rootfs assembly.

**Bundle constraints:** loaded via `file://` — a fully self-contained
bundle (inline or relative assets, no ES-module cross-origin imports,
no absolute URLs back to a dev server) renders without a local web
server; validated on hardware with the reference SPA 2026-08-02.
Bundles that genuinely need HTTP semantics belong in the URL model.
Reference/worked example: `recipes-graphics/wilhelmos-spa-demo/`.

## Runtime interface

### What the platform guarantees

- A cage Wayland session on tty1 running Cog fullscreen; the SPA is the
  page, never a window. Maintenance getty on tty2.
- Engine: WPE WebKit (version pinned per WilhelmOS release; policy in
  WEBKIT.md). Modern JS/CSS, canvas, WebGL, WASM, WebSockets. **Media
  playback beyond basic `video` support is off by default** (no MSE,
  no WebRTC, no Web Audio) — deployments needing it opt in per
  WEBKIT.md.
- `WILHELMOS_UI_SCALE` (platform default 1.5) is applied as the output
  device scale — the page sees it as `window.devicePixelRatio`.
- The web process runs sandboxed (bubblewrap: private namespaces,
  seccomp, filtered D-Bus — WEBKIT.md). Engine logs land in the
  persistent journal.
- Supervision: `Restart=always`, 2 s backoff, 10 s stop timeout — the
  session (and the page) restarts on any exit; only administrative
  `systemctl stop` keeps the display down.

### What the application must handle

- **Its own connectivity lifecycle.** In the URL model the server may
  be unreachable at boot or drop away mid-session; the page should
  reconnect/retry (a served SPA that goes blank on disconnect is a bad
  kiosk citizen). The platform restarts the browser, not the app's
  network sessions.
- **Viewport responsiveness** — panels range from 1080p to 4K; the
  page gets the full output at the configured scale.
- **No browser-chrome assumptions**: no tabs, no dialogs, no
  downloads, no printing; `window.open` etc. have no meaningful target.
- **gRPC backends need gRPC-Web** (a proxy such as tonic-web or Envoy)
  — browsers cannot speak native gRPC; true of any engine.

## Versioning

Contract versioned with WilhelmOS releases; this is v1. Incompatible
changes (web-root path, URL variables, scale semantics, engine feature
floor) bump the version and are recorded here and in DESIGN.md §10.
