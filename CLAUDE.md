# WilhelmOS

Minimal Linux distribution built with the Yocto Project (wrynose, 6.0 LTS), targeting QEMU x86-64 and bare-metal x86-64.

## Objective

WilhelmOS aims to be a **pre-hardened COTS platform for AL3-AL5 CNS/ATM ground equipment** under **ED-109A** ("Software Integrity Assurance Considerations for Communication, Navigation, Surveillance and Air Traffic Management Systems").

### Positioning

WilhelmOS is positioned as COTS software under ED-109A Section 12.4. The system integrator (applicant) builds their assurance case around it, rather than WilhelmOS needing full lifecycle evidence itself. The value proposition is reducing the applicant's certification burden by providing:

- A minimal, reproducible, hardened Linux platform purpose-built for ground equipment
- Certification evidence artifacts (SBOM, configuration management records, test results) that map to ED-109A Section 12.4.10 objectives
- A COTS Software Integrity Assurance Case template (per Section 12.4.11)


### Certification Strategy

1. **Linux kernel** — covered by COTS (Section 12.4) + service experience (Section 12.3.4) arguments
2. **WilhelmOS configuration** (Yocto recipes, image build) — provides reproducibility, SBOM, and configuration management evidence (Section 7)
3. **Safety monitoring** via systemd — satisfies Section 2.4.3 objectives (watchdog, service health, fault detection)
4. **Partitioning** — if WilhelmOS isolates application components, individual components can be assigned different ALs (Section 2.4.1)
5. **Application software** running on top — where the applicant focuses their full ED-109A lifecycle effort (Sections 4-8)
6. **PSAA** (Plan for Software Aspects of Approval, Section 11.1) — produced by the applicant, maps WilhelmOS evidence to ED-109A objectives

## User Experience

WilhelmOS supports two boot modes. **Option 2 is the primary mode** — it runs the sky_guard_client situation display, which is the core product.

### Option 1 — TTY mode (server / maintenance)
- Boot → systemd → auto-login → TUI on framebuffer console
- PSF bitmap font (Terminus), no GPU required, minimal footprint
- Used for sky_guard_server (headless), SWIM-style ATM web services (run resiliently under systemd with restart/priority/resource-cap policies — see docs/DESIGN.md §5), or system maintenance

### Option 2 — Graphical kiosk mode (critical)
- Boot → systemd → sky_guard_client (fullscreen OpenGL application)
- **sky_guard_client** is a situation display for ATM built with **wilhelm_renderer** (custom 2D OpenGL engine) + **Dear ImGui** for UI chrome
- Uses B612Mono TrueType font (aviation-specific, designed by Airbus for cockpit displays)
- Requires: GPU drivers (Mesa + DRM/KMS), OpenGL, GLFW; production hardware targets **integrated Intel/AMD graphics** (in-tree drivers, full hardware acceleration — discrete NVIDIA explicitly not targeted, see docs/DESIGN.md §4)
- Compositor: **cage** (minimal Wayland kiosk compositor) — required, since GLFW has no direct DRM/KMS backend; chosen over Weston for certification-scope reasons (see docs/DESIGN.md §4)
- UI scaling: `WILHELMOS_UI_SCALE` bitbake variable (default `"1.5"`, in the wilhelmos-kiosk-session recipe) — installs a `cage-kiosk.service.d` drop-in exporting it to the app; consumed by the wilhelmos_kiosk framework (its DESIGN.md §12); override per deployment, `""` = no drop-in (framework falls back to 1.0)
- The application stack: `systemd → cage → sky_guard_client → OpenGL → DRM/KMS → display`

### Related Projects

| Project | Repo | Purpose |
|---------|------|---------|
| wilhelm_renderer | `../wilhelm_renderer` | Custom 2D OpenGL rendering engine |
| wilhelm_renderer_imgui | `../wilhelm_renderer_imgui` | Dear ImGui integration for wilhelm_renderer |

## Build System

- **KAS** orchestrates the Yocto build. Config: `kas/qemu-wrynose.yaml`
- **Upstream repos:** openembedded-core (meta) + bitbake + meta-yocto (meta-poky, meta-yocto-bsp) + meta-openembedded (meta-oe) — Yocto 5.3+ has no poky combined repo; all pinned to exact commit SHAs in `kas/qemu-wrynose.yaml` (update pins via `git ls-remote`)
- **Custom layer:** `meta-wilhelmos/` (priority 6)
- **All build state lives outside the repo** in `KAS_WORK_DIR` (default `../wilhelmos-build`, exported by the Makefile): upstream layer clones, `build/`, `downloads/`, `sstate-cache/`. The checkout contains only WilhelmOS sources. When running `kas` directly (not via `make`), export `KAS_WORK_DIR` first — otherwise kas clones/builds into the current directory
- **kas overlays** (compose with `:`): `kas/debug.yaml` (verbose-boot wks), `kas/hw.yaml` (MACHINE=genericx86-64, USB-bootable kiosk for bare metal — adds Intel GPU firmware + i915/xe/amdgpu modules via gpu.cfg)
- **Graphical QEMU:** `runqemu wilhelmos-image-kiosk kvm sdl slirp serialstdio` inside `kas shell` (kas passes DISPLAY/XAUTHORITY through via the `env:` block); add `wic ovmf` to boot the real UEFI/systemd-boot chain; add `gl` for virgl acceleration

## Bare-Metal Diagnosis

After a hardware test, the boot USB stick is plugged back into the build host and is available for post-mortem: mount it (`udisksctl mount -b /dev/sdX2`, label `rootfs`) and inspect the flashed rootfs directly. journald is persistent on the image, so the failed boot's logs are on the stick (reading the journal and `/etc/shadow` requires root).

Claude's sandboxed shell has no TTY for sudo, and sudo's credential cache is per-terminal — so the user must run the privileged copy in their own terminal, then Claude analyzes the readable copy:

```bash
sudo sh -c 'D=/var/tmp/wilhelmos-postmortem; rm -rf $D; cp -a /run/media/aludin/rootfs/var/log/journal $D; cp /run/media/aludin/rootfs/etc/shadow $D/shadow.txt; chown -R aludin:aludin $D'
```

Then: `journalctl -D /var/tmp/wilhelmos-postmortem --list-boots`, `journalctl -D ... -b <boot> -p err`, etc.

## Make Targets

- `make build` — build image via `kas build`
- `make run` — launch in QEMU (nographic)
- `make shell` — open KAS shell with bitbake
- `make clean` — remove `build/`
- `make distclean` — remove `build/`, `../downloads`, `../sstate-cache`

## Architecture

- **Distro:** wilhelmos (extends poky.conf), version 0.1.0
- **Machine:** qemux86-64
- **Image:** wilhelmos-image-base
- **Init:** systemd (no sysvinit)
- **Boot:** systemd-boot (UEFI/GPT), OVMF firmware for QEMU
- **Disk layout:** `meta-wilhelmos/wic/wilhelmos-efi.wks`

## Layer Structure

```
meta-wilhelmos/
  conf/
    layer.conf                  # Layer registration
    distro/wilhelmos.conf       # Distro policy, features, user setup
  recipes-core/
    images/                     # wilhelmos-image-base + wilhelmos-image-kiosk
    busybox/                    # Disable busybox syslog (journald replaces it)
    base-files/                 # vconsole.conf (fr_CH keymap, Terminus font)
    systemd/                    # Persistent journald drop-in
  recipes-fonts/                # Terminus console font package
  recipes-graphics/
    wlroots/                    # wlroots 0.19 (cage dependency)
    cage/                       # cage compositor + wilhelmos-kiosk-session service
    wilhelm-renderer/           # wilhelm_renderer_imgui demo app (cargo recipe)
  recipes-kernel/               # Kernel config appends (USB/EFI/ext4 + hardening)
  recipes-security/             # Sudoers policy (wheel group, password required)
  files/wic/                    # WIC disk image layouts (production + debug)
```

## User Setup

- Root is locked (`password = '!'`)
- Default user: `wilhelmos` (password: `wilhelmos`), member of `wheel` group — **dev-only credential**; production images must override `EXTRA_USERS_PARAMS`
- Wheel group has full sudo (password required) via `/etc/sudoers.d/10-wheel`

## Backlog

See [TODO.md](TODO.md) for:
- General improvements (recipe metadata, Makefile, kernel config)
- ED-109A certification roadmap (evidence artifacts, hardening, monitoring, documentation)

## Conventions

- Recipes use modern Yocto syntax (`:append`, `:remove`, not `_append`)
- Recipe overrides go in `.bbappend` files with `%` version wildcard
- KAS config variable: `KAS_FILE` (defaults to `kas/qemu-wrynose.yaml`)
