# WPE WebKit Integration Reference (SPA profile)

Engine-level companion to [DESIGN.md §10](DESIGN.md) (the SPA profile
decision record). This documents how WPE WebKit is configured in
WilhelmOS, the sandbox architecture, and the integration findings from
the 2026-08-02 bring-up. Recipes live in the `meta-webkit` layer
(pinned in `kas/spa.yaml`); WilhelmOS policy is applied via
`meta-wilhelmos/dynamic-layers/webkit/…/wpewebkit_%.bbappend`, which
only activates when meta-webkit is present.

## Components

| Component | Role |
|---|---|
| `wpewebkit` | The engine: upstream WebKit's embedded-Linux port (Igalia) |
| `cog` | Minimal kiosk launcher — loads one URL fullscreen as a Wayland client under cage; no browser UI exists |
| `libwpe` / `wpebackend-fdo` | The backend glue: exports rendered frames to the compositor via EGL/dmabuf |
| `bubblewrap` (`bwrap`) + `xdg-dbus-proxy` | External sandbox helpers exec'd by the engine (see below) |

The session stack: `systemd → cage-spa.service → cage → cog → WPEWebKit
→ EGL → DRM/KMS`. Cog receives the URL and scale from
`wilhelmos-spa-launch` (`WILHELMOS_SPA_URL`, `WILHELMOS_UI_SCALE` →
`--device-scale`).

**URL configuration layers.** (1) Build time: the `WILHELMOS_SPA_URL`
bitbake variable bakes a drop-in into the image (fleet deployments).
(2) Runtime, operator (tty2): `sudo wilhelmos-spa-url set <url>` /
`show` / `clear` — writes `/etc/systemd/system/cage-spa.service.d/`
`50-operator-url.conf`, which overrides the image drop-in by systemd
precedence, and restarts the session. (3) Future: a field in the
planned tty2 diagnostics TUI, backed by the same tool. Note for the
Phase 2 read-only-rootfs design: the operator override relies on a
writable `/etc` — the mechanism must move to whatever writable-config
location that design chooses.

## PACKAGECONFIG policy (the bbappend, with reasons)

- **Removed `mediasource mediastream webaudio`** — the heavy GStreamer
  subtrees (plugins-good/-bad), per the §10 media-minimal decision.
- **`video` kept ON — involuntarily.** WPE 2.52 does not build with
  `ENABLE_VIDEO=OFF`: image-decoder sources include GStreamer headers
  unconditionally (upstream never CI-builds that combination). GStreamer
  core + plugins-base is therefore the engine's floor. Revisit on engine
  upgrades.
- **Removed `speech-synthesis`** — depends on `flite` from
  meta-multimedia (a layer WilhelmOS does not carry); no use on a
  closed display.
- **Added `journald`** — engine logs land in the persistent journal
  like every other platform component.
- **Added `bubblewrap`** — the web-process sandbox (below).
- **`remote-inspector` still enabled** — bring-up debugging aid;
  removal is Phase D closure together with the origin allowlist.
- **`DEPENDS += virtual/libgbm`** — the recipe's `gbm` option declares
  only libdrm, but configure needs Mesa's libgbm in the sysroot
  ("Could NOT find GBM").
- **`RDEPENDS += bubblewrap xdg-dbus-proxy`** — see the sandbox
  section; without this the image is broken by construction.

Version preferences (in `kas/spa.yaml`): meta-webkit carries stale
copies of the sandbox helpers (bubblewrap 0.8.0 no longer compiles
under the C23-default toolchain); meta-oe's current recipes are
preferred (`PREFERRED_VERSION_bubblewrap`, `…_xdg-dbus-proxy`) —
they otherwise lose to meta-webkit's higher layer priority.

## The web-process sandbox

WebKit is multi-process: cog is the trusted *UI process*; per page it
spawns a *network process*, a *GPU process*, and the **web process** —
the one that parses HTML, decodes images, and runs JavaScript, i.e.
where hostile input meets a multi-million-line C++ codebase.
Historically, browser exploits land there. The sandbox exists so a
compromised web process is contained.

**Mechanism.** `bwrap` is not part of WebKit: it is a small, audited
external tool (from Flatpak) that the engine *execs* to build the cell
from kernel primitives, unprivileged:

- **Namespaces** (mount/PID/user — kernel needs `CONFIG_USER_NS`,
  present in the WilhelmOS kernel): the web process sees a private,
  minimal read-only filesystem, no `/home`, no journal, no other
  processes.
- **seccomp**: syscall allowlist — `mount`, `ptrace`, module loading
  etc. refused by the kernel even under code execution.
- **no-new-privileges**: no setuid escalation.
- **`xdg-dbus-proxy`**: the sandbox gets a filtered D-Bus proxy socket,
  not the real bus.

**Build/runtime contract.** `ENABLE_BUBBLEWRAP_SANDBOX=ON` (our
`bubblewrap` PACKAGECONFIG) compiles the logic in and hard-bakes the
helper paths (`/usr/bin/bwrap`, `/usr/bin/xdg-dbus-proxy`). At runtime
the engine **fails closed**: sandbox compiled in + helper missing =
SIGABRT, not silent unprotected operation.

**The bring-up incident (2026-08-02).** meta-webkit's PACKAGECONFIG
declares the helpers as build-time DEPENDS only — they were never
installed into the image. First GMKtec boot: cog aborted ~1 s after
cage started ("Failed to spawn child process “/usr/bin/bwrap”"),
`Restart=always` produced a 2 s blink loop (VT seized and released
every cycle, also blinking the tty2 getty). Fix: the bbappend's
`RDEPENDS`. Worth reporting upstream to meta-webkit — any user of
their `bubblewrap` PACKAGECONFIG ships this broken image.

**Why this matters for the profile.** In the URL model the device
renders server-delivered content for years; a compromised server or
content path exploiting an engine bug is the realistic attack. With the
sandbox, the attacker holds a process that cannot read platform
configuration, touch journald/systemd, see the maintenance tty, or
persist — blast radius ≈ wrong pixels until restart. For ED-109A this
is a partitioning-flavored argument *inside* the browser COTS
component: only the small UI-process side is fully trusted.

## QEMU vs hardware — what each can validate

- **QEMU (software GL)** boots the session and proves the machinery
  (unit ordering, launcher, contract paths, process tree) but **cannot
  render WPE content**: wpebackend-fdo requires a real EGL/dmabuf path;
  llvmpipe cannot export dmabufs (cog logs `libEGL warning: failed to
  get driver name for fd -1`). virgl acceleration hangs against this
  build host's NVIDIA GL (cog in D-state). Additionally, on the
  degraded path the engine **silently skips the sandbox** — a QEMU run
  showing an unsandboxed web process is *not* evidence about hardware
  behavior, in either direction.
- **Hardware (real iGPU)** is the only valid target for pixel and
  sandbox validation: amdgpu/i915 provide the native EGL/dmabuf path
  WPE is designed for (log signature of health: cage reports the
  radeonsi/iris renderer, cog spawns WPENetworkProcess/WPEWebProcess
  and stays up; web-process namespaces differ from init's).

## Footprint

Bare-metal rootfs delta vs the native kiosk image: **+306 MB**
(wilhelmos-image-spa 2080 MB vs wilhelmos-image-kiosk 1774 MB) —
libWPEWebKit plus ICU, HarfBuzz, libsoup, and the GStreamer floor.
Phase D lever if needed: the `reduce-size` PACKAGECONFIG
(MinSizeRel build).
