# WilhelmOS - Improvement Backlog

## High Priority

- [ ] Fix Terminus font license: change `LICENSE = "CLOSED"` to `LICENSE = "OFL-1.1"` with proper `LIC_FILES_CHKSUM` in `wh-terminus-console-font.bb`
- [ ] Add missing recipe metadata (`SUMMARY`, `DESCRIPTION`, `HOMEPAGE`, `LIC_FILES_CHKSUM`) to:
  - [ ] `recipes-fonts/terminus-console-font/wh-terminus-console-font.bb`
  - [ ] `recipes-security/wilhelmos-sudoers/wilhelmos-sudoers.bb`
  - [ ] `recipes-core/base-files/wilhelmos-vconsole-conf_1.0.bb`
- [ ] Add `RDEPENDS:${PN} += "sudo"` to `wilhelmos-sudoers.bb`
- [ ] Add `LAYERDEPENDS_wilhelmos = "core openembedded-layer"` to `meta-wilhelmos/conf/layer.conf`

## Medium Priority

- [ ] Makefile improvements:
  - [ ] Add default target (`.DEFAULT_GOAL := build`)
  - [ ] Add `help` target listing available commands
  - [ ] Add KAS availability check before build
- [ ] Kernel config (`usb-root.cfg`):
  - [ ] Add `CONFIG_EFI_STUB=y` and `CONFIG_VFAT_FS=y`
  - [ ] Add section comments explaining each config group
- [ ] Clean up `wilhelmos-efi.wks`: remove commented-out QEMU debug boot parameters or move to a separate debug WKS

## Low Priority

- [ ] Update KAS header version from 11 to latest supported (check with `kas --version`)
- [ ] Add cross-reference comments between busybox syslog disabling (bbappend) and distro conf (`VIRTUAL-RUNTIME_syslog`)

---

## User Experience — Boot Modes

### Option 2 — Graphical kiosk mode (CRITICAL — primary use case)

sky_guard_client is an OpenGL situation display for ATM. It uses wilhelm_renderer (custom 2D engine), Dear ImGui, GLFW, and B612Mono font. This is the core product mode.

- [ ] Add GPU/DRM/KMS kernel support (CONFIG_DRM, CONFIG_DRM_I915, CONFIG_DRM_AMDGPU, etc.)
- [ ] Add Mesa OpenGL drivers to image (meta-oe or custom recipe)
- [ ] Add GLFW library to image (required by wilhelm_renderer for window/context creation)
- [ ] Determine if GLFW can run directly on DRM/KMS or needs a Wayland compositor
- [ ] If compositor needed: add cage (minimal single-app Wayland compositor)
- [ ] Add freetype/fontconfig for TrueType font rendering (if not bundled in wilhelm_renderer)
- [ ] Ship B612Mono-Regular.ttf font (aviation-specific, used by sky_guard_client)
- [ ] Create systemd service to auto-launch sky_guard_client fullscreen on boot
- [ ] Evaluate image size impact (Mesa + GPU drivers + GLFW + fonts vs base image)
- [ ] Cross-compile sky_guard_client + wilhelm_renderer for the target (Yocto SDK or cargo-cross)

### Option 1 — TTY mode (server / maintenance)

Used for sky_guard_server (headless, no GPU) or system maintenance access.

- [ ] systemd getty autologin override for `wilhelmos` user on tty1
- [ ] Auto-launch TUI or shell from user profile
- [ ] Evaluate PSF fonts for console use (Terminus, Spleen)
- [ ] Set framebuffer resolution via kernel `video=` parameter
- [ ] Keep login shell on tty2 (`Alt+F2`) for maintenance access when running kiosk mode

---

## ED-109A Certification Roadmap

WilhelmOS is positioned as COTS software (ED-109A Section 12.4) for AL3-AL5 CNS/ATM ground equipment. The roadmap below maps improvements to specific ED-109A sections and objectives.

### Reproducibility & Configuration Management (ED-109A Section 7)

These items produce evidence for Software Configuration Management objectives (Annex A, Table A-8) — configuration identification (7.2.1), baselines and traceability (7.2.2), and archive/retrieval (7.2.7).

- [ ] Pin all upstream repos to exact commit SHAs (not branch names) in `kas/qemu-kirkstone.yaml`
- [ ] Archive download sources for offline reproducible builds
- [ ] Enable and verify reproducible builds (`INHERIT += "reproducible_build"`)
- [ ] Generate SBOM (Software Bill of Materials) using Yocto's built-in support (`INHERIT += "create-spdx"`)
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

- [ ] Enable hardware watchdog timer support (kernel + systemd `RuntimeWatchdogSec`)
- [ ] Configure systemd service restart policies for critical services
- [ ] Add persistent logging to survive reboots (`Storage=persistent` in journald.conf)
- [ ] Consider A/B partition scheme for safe updates and rollback (supports Section 2.5.4 cutover/hot swapping)

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
- [ ] Add image size and package manifest regression checks
- [ ] Implement kernel config verification (`bitbake -c kernel_configcheck`)
- [ ] Define structural coverage targets appropriate to target AL (Table A-7)
- [ ] Requirements-based test selection aligned with Section 6.4.2
