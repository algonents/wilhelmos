# WilhelmOS Kiosk Application Contract (v1)

The interface between the WilhelmOS platform and the kiosk application
an integrator runs on it. The design rationale lives in
[DESIGN.md §7](DESIGN.md); this document is the normative reference an
integrator builds — and certifies — against.

**Certification framing.** The layer boundary is the certification
boundary: WilhelmOS is COTS software under ED-109A §12.4, pinned by the
integrator as a release tag; everything in the integrator's own layer
(application recipe, image recipe, production credentials) is the
applicant's ED-109A scope. Nothing application-specific exists in
`meta-wilhelmos`, and nothing in the integrator's layer modifies
platform recipes.

## Composition: how an application is brought in

The integrator maintains their own Yocto layer (`meta-<customer>`) and
composes it with a pinned WilhelmOS release in their own kas
configuration:

```yaml
# meta-<customer>/kas/kiosk.yaml — in the integrator's repository
header:
  version: 14
  includes:
    - repo: wilhelmos
      file: kas/hw.yaml            # inherit the platform build configuration
repos:
  wilhelmos:
    url: <wilhelmos git URL>
    commit: <release tag SHA>      # the pinned COTS baseline (§12.4)
  meta-customer:
    path: .
local_conf_header:
  kiosk-app: |
    KIOSK_APP = "<application-recipe-name>"
```

The dependency arrow only ever points from integrator to platform: the
integrator's kas file includes WilhelmOS; WilhelmOS never references the
integrator. An integrator image recipe may equivalently `require` the
platform's `wilhelmos-image-kiosk.bb` and set `KIOSK_APP` there.

## Packaging obligations (build time)

The application recipe inherits `kiosk-app.bbclass` and names the
executable it installs:

```bitbake
inherit kiosk-app
KIOSK_APP_BINARY = "${bindir}/my_app"
```

The class then:

- claims the application role (`PROVIDES virtual/kiosk-app`,
  `RPROVIDES kiosk-app`) — exactly one package per image may hold it;
- installs the stable exec path `/usr/libexec/kiosk-app` as a relative
  symlink to `KIOSK_APP_BINARY`;
- fails `do_install` if the binary is missing or not executable;
- requires the `wayland` and `opengl` distro features.

Enforcement is two-stage and entirely at build time: the class check
above, plus a rootfs check in the kiosk image that fails the build if
the selected `KIOSK_APP` package did not install an executable
`/usr/libexec/kiosk-app`.

## Runtime interface

### What the platform guarantees

- A cage Wayland session on tty1, started by `cage-kiosk.service` once
  seatd and (when present) a GPU render node are ready. The application
  is exec'd as `/usr/libexec/kiosk-app` — argument-less — and is the
  session's only client; cage displays its single toplevel fullscreen.
- Process environment: `WAYLAND_DISPLAY` (set by cage),
  `XDG_RUNTIME_DIR=/run/cage`, and optionally `WILHELMOS_UI_SCALE`
  (platform default `1.5`; absent when a deployment sets the bitbake
  variable to `""` — the application falls back to 1.0).
- Runs as the unprivileged system user `kiosk` (groups: video, input,
  render, seat). Writable home at `/home/kiosk`.
- GL via EGL/Wayland (Mesa). No X11 in the image; on hardware without a
  supported GPU the session falls back to software rendering.
- stdout/stderr are captured by the journal (persistent across boots).
- Supervision (ED-109A §2.4.3): `Restart=always` with 2 s backoff —
  every exit, clean or not, restarts the application; only an explicit
  administrative `systemctl stop` keeps the display down.
- A maintenance getty on tty2; the operator seat has no way to kill or
  escape the application.

### What the application must do

- Be a Wayland client presenting a single surface, driven from the
  environment above (no display/device probing, no X11 fallback).
- Log to stdout/stderr only; do not daemonize or spawn services.
- On SIGTERM, shut down in an orderly fashion and exit 0 — within the
  unit's 10 s stop timeout (`TimeoutStopSec`), after which it is killed.
- Exit nonzero on fatal errors (panic → log → nonzero exit is the
  expected framework posture); the supervisor restarts either way, but
  the exit code is the health signal in the journal.
- Honor `WILHELMOS_UI_SCALE` if UI scaling is supported (applications
  built on the optional `wilhelmos_kiosk` framework get this for free).

## Versioning

The contract is versioned with WilhelmOS releases; this is v1.
Incompatible changes (exec path, environment, exit semantics, class
variables) bump the version and are recorded here and in DESIGN.md §7.
The reference implementation and worked packaging example is
`wilhelmos-kiosk-demo` (`recipes-graphics/wilhelmos-kiosk-demo/`).
