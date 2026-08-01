# WilhelmOS - Improvement Backlog

See [docs/DESIGN.md](docs/DESIGN.md) for the phased roadmap and sequencing rationale.

## High Priority

- [x] Fix Terminus font license: change `LICENSE = "CLOSED"` to `LICENSE = "OFL-1.1"` with proper `LIC_FILES_CHKSUM` in `wh-terminus-console-font.bb`
- [x] Add missing recipe metadata (`SUMMARY`, `DESCRIPTION`, `HOMEPAGE`, `LIC_FILES_CHKSUM`) to:
  - [x] `recipes-fonts/terminus-console-font/wh-terminus-console-font.bb`
  - [x] `recipes-security/wilhelmos-sudoers/wilhelmos-sudoers.bb`
  - [x] `recipes-core/base-files/wilhelmos-vconsole-conf_1.0.bb`
- [x] Add `RDEPENDS:${PN} += "sudo"` to `wilhelmos-sudoers.bb`
- [x] Add `LAYERDEPENDS_wilhelmos = "core openembedded-layer"` to `meta-wilhelmos/conf/layer.conf`
- [x] Migrate from kirkstone (EOL April 2026) to wrynose (6.0 LTS, supported until April 2030) — done 2026-07-23; see DESIGN.md §3

## Medium Priority

- [x] Makefile improvements:
  - [x] Add default target (`.DEFAULT_GOAL := build`)
  - [x] Add `help` target listing available commands
  - [x] Add KAS availability check before build
- [x] Kernel config (`usb-root.cfg`):
  - [x] Add `CONFIG_EFI_STUB=y` and `CONFIG_VFAT_FS=y`
  - [x] Add section comments explaining each config group
- [x] Clean up `wilhelmos-efi.wks`: remove commented-out QEMU debug boot parameters or move to a separate debug WKS (now `wilhelmos-efi-debug.wks` + `kas/debug.yaml`)

## Disk Install Follow-ups (from first internal-disk install, 2026-07-29)

- [x] Grow the rootfs to fill the target disk — done at install time in `wilhelmos-install` (sfdisk grow + resize2fs), not on first boot: no extra boot-time machinery in the image, and it can't trigger on stick boots. Interim until the Phase 2 A/B slot layout (DESIGN.md §6) re-lays the disk
- [ ] Decision record: emergency/rescue mode with locked root = unreachable console on an appliance (fail-closed vs. maintenance credential; observed during the fstab boot failure)
- [ ] Report upstream wic bug: per-partition `--no-fstab-update` parsed but ignored by wic 0.3.1 `update_fstab` (workaround: imager-global flag via `WIC_CREATE_EXTRA_ARGS`, see wilhelmos-image-base.bb)
- [ ] Deduplicate `root=` on the kernel cmdline: wks `--append` duplicates the `root=` that wic already generates (installer's sed handles it with /g, but the duplication is noise)
- [x] Document the disk-install procedure — docs/INSTALLATION.md (install medium anatomy, flash + install steps, scaling/fleet roadmap); link it from README when the README gets its next pass
- [ ] GMKtec BIOS: RTC is ~6 weeks behind; NTP/time-sync story for deployed ground equipment

## Low Priority

- [x] Update KAS header version from 11 to latest supported (now 14, requires kas >= 4.0)
- [x] Add cross-reference comments between busybox syslog disabling (bbappend) and distro conf (`VIRTUAL-RUNTIME_syslog`)

---

## User Experience — Boot Modes

### Option 2 — Graphical kiosk mode (CRITICAL — primary use case)

sky_guard_client is an OpenGL situation display for ATM. It uses wilhelm_renderer (custom 2D engine), Dear ImGui, GLFW, and B612Mono font. This is the core product mode.

Stack validation is DONE (2026-07-24, see DESIGN.md §4): the wilhelm_renderer_imgui demo runs fullscreen under cage in wilhelmos-image-kiosk, verified in QEMU.

- [x] Add GPU/DRM/KMS kernel support — virtio built in for QEMU; gpu.cfg adds i915/xe/amdgpu modules + SimpleDRM fallback for bare metal (Intel firmware installed on genericx86-64 via kas/hw.yaml)
- [x] Add Mesa OpenGL drivers to image (via wilhelm-renderer-demo RDEPENDS: libegl-mesa, libgbm, mesa-megadriver)
- [x] Add GLFW library to image — not needed as a package: wilhelm_renderer_sys vendors and statically links GLFW 3.4 (Wayland-only via GLRENDERER_BUILD_X11=OFF)
- [x] Determine if GLFW can run directly on DRM/KMS or needs a Wayland compositor — it cannot (GLFW is X11/Wayland only); compositor required, **cage** chosen (see DESIGN.md §4)
- [x] Add cage recipe (+ wlroots) — wlroots 0.19.3 + cage recipes in recipes-graphics/; no Weston scaffold was needed
- [x] Add freetype for TrueType rendering — vendored/statically linked in wilhelm_renderer_sys; fontconfig not needed
- [ ] Ship B612Mono-Regular.ttf font (aviation-specific, used by sky_guard_client)
- [x] Create systemd service to auto-launch the kiosk app fullscreen on boot (wilhelmos-kiosk-session / cage-kiosk.service; runs the validation demo — switch to sky_guard_client later)
- [ ] Evaluate image size impact (Mesa + GPU drivers + fonts vs base image)
- [ ] Cross-compile sky_guard_client for the target (wilhelm_renderer cross-compile proven by the demo recipe)
- [ ] Merge + publish the feat/kiosk-validation crate changes (version bumps, crates.io), then switch wilhelm-renderer-demo to crate:// fetching
- [ ] Fix shutdown hang on cage-kiosk: `shutdown now` from tty2 leaves the stop job waiting on cage (no `TimeoutStopSec`, so systemd waits the 90s default before SIGKILL; cage/app don't exit on SIGTERM while the seat is disabled after a VT switch — DRM FD paused, no frame callbacks). Bound it with `TimeoutStopSec=` in cage-kiosk.service; investigate clean SIGTERM handling in the kiosk framework when the surface is hidden

### Option 1 — TTY mode (server / maintenance)

Used for sky_guard_server (headless, no GPU) or system maintenance access.

- [ ] systemd getty autologin override for `wilhelmos` user on tty1
- [ ] Auto-launch TUI or shell from user profile
- [ ] Evaluate PSF fonts for console use (Terminus, Spleen)
- [ ] Fix console font reset on GPU driver bind: Terminus (ter-u32n) is applied early against SimpleDRM, but when the native driver (amdgpu/i915) takes over fbcon the vtcon re-registers and consoles fall back to the kernel's built-in 8×16 font — unreadable on high-DPI panels. The udev re-trigger (90-vconsole.rules) fires but systemd-vconsole-setup can't copy fonts to remaining VTs (`KD_FONT_OP_GET failed: Invalid argument`/`I/O error` — tty1 is in graphics mode under cage). Candidate fix: build `CONFIG_FONT_TER16x32=y` into the kernel and select it via `fbcon=font:TER16x32` on the cmdline, making the fbcon default itself Terminus — immune to vtcon handover
- [ ] Set framebuffer resolution via kernel `video=` parameter
- [x] Keep login shell on tty2 (`Alt+F2`) for maintenance access when running kiosk mode (getty@tty2 enabled by wilhelmos-kiosk-session)
- [ ] Diagnostics TUI on tty2 (kiosk mode): first-class operator surface showing essential diagnostics at a glance (service health, GPU/display state, network, recent faults) without requiring shell commands — tty2 is as important as the kiosk app itself, not merely maintenance access; depends on the deterministic console font fix (fbcon TER16x32) and pairs with the `video=` per-deployment resolution knob

### SPA profile (planned — decision record in DESIGN.md §10)

Fullscreen closed-environment web app (Cog → WPE WebKit under cage) for
integrators deploying single-page apps instead of native GL. Engine,
delivery model, and media default decided 2026-08-01; implementation phased:

- [ ] Phase B — prototype: pin meta-webkit (wrynose branch), build cog + wpewebkit under cage in QEMU, load a sample SPA; verify web-process sandbox (bubblewrap/user-namespace kernel config); resolve bundled-SPA serving (`file://` vs custom URI scheme vs localhost server) and the web-app config-injection analog of the env-var drop-in
- [ ] Phase C — contract machinery: `spa-app.bbclass`, `wilhelmos-image-spa`, `wilhelmos-spa-session`, reference SPA (wilhelmos-kiosk-demo analog), `docs/SPA-CONTRACT.md`
- [ ] Phase D — closure: enforce the closed-environment requirements in production images (origin allowlist, inspector off, feature switches); certification notes on the per-profile COTS surface (browser engine vs native stack)

---

## Knowledge Transfer (teaching sessions)

Guided walkthroughs at teaching pace — understanding the system, not just operating it:

- [ ] Boot sequence walkthrough: UEFI → systemd-boot → kernel (SimpleDRM → native GPU driver bind) → systemd targets → seatd/cage → kiosk app (requested 2026-07-25)
- [ ] Bare-metal post-mortem diagnosis & journal reading (requested 2026-07-27): the persistent-journald workflow — mounting the stick, copying the journal, `journalctl -D`/`--list-boots`/`-F _BOOT_ID`/`-p err`, identifying hardware from DMI/PCI lines, spotting firmware-load failures, following a systemd shutdown sequence, and how the udev → vconsole-setup → fbcon interplay was traced (use the GMKtec amdgpu-firmware and font-reset cases as worked examples)

---

## ED-109A Certification Roadmap

WilhelmOS is positioned as COTS software (ED-109A Section 12.4) for AL3-AL5 CNS/ATM ground equipment. The roadmap below maps improvements to specific ED-109A sections and objectives.

### Reproducibility & Configuration Management (ED-109A Section 7)

These items produce evidence for Software Configuration Management objectives (Annex A, Table A-8) — configuration identification (7.2.1), baselines and traceability (7.2.2), and archive/retrieval (7.2.7).

- [x] Pin all upstream repos to exact commit SHAs (not branch names) in the kas config
- [ ] Archive download sources for offline reproducible builds
- [x] Enable and verify reproducible builds (`BUILD_REPRODUCIBLE_BINARIES = "1"` asserted; kirkstone has no separate class) — bit-for-bit verification still pending
- [x] Generate SBOM (Software Bill of Materials) using Yocto's built-in support (`INHERIT += "create-spdx"`)
- [ ] Establish a version baselining strategy (tag releases, freeze configs per baseline)
- [ ] Produce Software Configuration Index (Section 11.16) for each release

### Image Hardening

Supports the COTS software restriction of functionality strategy (Section 12.4.11) — reducing the COTS scope that the applicant must account for.

- [ ] Implement read-only root filesystem (overlay for volatile data)
- [ ] Strip unnecessary kernel modules — audit and minimize kernel config
- [ ] Remove or disable unused systemd services
- [ ] Disable debug interfaces (serial console, SSH) in production image variants
- [ ] Add filesystem integrity checking (dm-verity or IMA/EVM)

### Safety Monitoring & Resilience (ED-109A Section 2.4.3)

Safety monitoring allows the monitored software to be assigned the AL associated with loss of the monitored function, provided the monitor is independent and at the appropriate AL.

- [ ] Enable hardware watchdog timer support (kernel + systemd `RuntimeWatchdogSec`) — Phase 2, see DESIGN.md §6
- [ ] Configure systemd service restart policies for critical services — Phase 2
- [x] Add persistent logging to survive reboots (`Storage=persistent` via `wilhelmos-journald-conf` + `VOLATILE_LOG_DIR = "no"`)
- [ ] Implement the two-path update architecture (decided, see DESIGN.md §6): A/B platform slots ("OS patching", infrequent) + independent application slot pair (frequent, emergency-capable), atomic image-based with rollback (Section 2.5.4); candidate tooling RAUC

### COTS Evidence Package (ED-109A Section 12.4)

The applicant needs evidence to satisfy additional COTS objectives (Section 12.4.10, Annex A tables). WilhelmOS should provide as much of this as possible to reduce the applicant's burden.

- [ ] COTS Software Planning: document WilhelmOS scope, known limitations, acceptance criteria (Section 12.4.3)
- [ ] COTS Software Acquisition: provide problem reports, release notes, configuration data (Section 12.4.4)
- [ ] COTS Verification evidence: test results for the shipped configuration (Section 12.4.5)
- [ ] COTS SCM records: version history, change tracking, baseline identification (Section 12.4.6)
- [ ] Service experience data for Linux kernel: deployment history, known problem reports, resolution tracking (Section 12.3.4)
- [ ] COTS Software Integrity Assurance Case template for applicants (Section 12.4.11)

### Lifecycle Documentation (ED-109A Section 11)

- [ ] PSAA template — Plan for Software Aspects of Approval (Section 11.1)
- [ ] Establish requirements traceability framework: high-level requirements -> tests (Section 5.5)
- [ ] Define software verification strategy with coverage targets per AL (Section 6)
- [ ] Tool qualification plan for build toolchain — GCC, BitBake, KAS (Section 12.2)

### Testing & Verification (ED-109A Section 6)

Supports verification objectives in Annex A, Tables A-3 through A-7. The level of structural coverage depends on the target AL (Table A-7): decision coverage for AL1-AL2, statement coverage for AL3.

- [ ] Add automated boot-to-login integration tests (QEMU-based CI)
- [x] Boot the .wic image in QEMU via OVMF (`runqemu wic ovmf`) to exercise the real UEFI → systemd-boot → GPT chain — done 2026-07-24, full kiosk validation 9/9 through the UEFI path
- [ ] Add image size and package manifest regression checks
- [ ] Implement kernel config verification (`bitbake -c kernel_configcheck`)
- [ ] Define structural coverage targets appropriate to target AL (Table A-7)
- [ ] Requirements-based test selection aligned with Section 6.4.2
