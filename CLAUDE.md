# WilhelmOS

Minimal Linux distribution built with the Yocto Project (Kirkstone branch), targeting QEMU x86-64 and bare-metal x86-64.

## Objective

WilhelmOS aims to be a **pre-hardened COTS platform for AL3-AL5 CNS/ATM ground equipment** under **ED-109A** ("Software Integrity Assurance Considerations for Communication, Navigation, Surveillance and Air Traffic Management Systems").

### Positioning

WilhelmOS is positioned as COTS software under ED-109A Section 12.4. The system integrator (applicant) builds their assurance case around it, rather than WilhelmOS needing full lifecycle evidence itself. The value proposition is reducing the applicant's certification burden by providing:

- A minimal, reproducible, hardened Linux platform purpose-built for ground equipment
- Certification evidence artifacts (SBOM, configuration management records, test results) that map to ED-109A Section 12.4.10 objectives
- A COTS Software Integrity Assurance Case template (per Section 12.4.11)

### ED-109A Assurance Level Targeting

ED-109A defines six Assurance Levels (AL1 through AL6), mapped to failure condition severity. AL4 has no airborne equivalent and is unique to ground systems.

| AL Level | Viability | Rationale |
|----------|-----------|-----------|
| AL5-AL6 | Strong | Statement coverage sufficient (AL5), no guidance needed (AL6). Service experience argument (Section 12.3.4) is sufficient for Linux. |
| AL4 | Strong | No associated failure condition category. Light objectives. Unique to ground systems. |
| AL3 | Feasible | Needs decision coverage and more documentation, but no MC/DC. COTS framework (Section 12.4) + service experience carries significant weight. |
| AL1-AL2 | Not targeted | Requires MC/DC-like structural coverage (Annex A, Table A-7), full independence, and complete traceability. Impractical for the Linux kernel. |

### Certification Strategy

1. **Linux kernel** — covered by COTS (Section 12.4) + service experience (Section 12.3.4) arguments
2. **WilhelmOS configuration** (Yocto recipes, image build) — provides reproducibility, SBOM, and configuration management evidence (Section 7)
3. **Safety monitoring** via systemd — satisfies Section 2.4.3 objectives (watchdog, service health, fault detection)
4. **Partitioning** — if WilhelmOS isolates application components, individual components can be assigned different ALs (Section 2.4.1)
5. **Application software** running on top — where the applicant focuses their full ED-109A lifecycle effort (Sections 4-8)
6. **PSAA** (Plan for Software Aspects of Approval, Section 11.1) — produced by the applicant, maps WilhelmOS evidence to ED-109A objectives

## Build System

- **KAS** orchestrates the Yocto build. Config: `kas/qemu-kirkstone.yaml`
- **Upstream layers:** Poky (meta, meta-poky, meta-yocto-bsp) + meta-openembedded (meta-oe), both on kirkstone
- **Custom layer:** `meta-wilhelmos/` (priority 6)
- Shared download/sstate dirs live one level up: `../downloads`, `../sstate-cache`

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
    images/                     # wilhelmos-image-base image recipe
    busybox/                    # Disable busybox syslog (journald replaces it)
    base-files/                 # vconsole.conf (fr_CH keymap, Terminus font)
  recipes-fonts/                # Terminus console font package
  recipes-kernel/               # Kernel config appends (USB, EFI, ext4)
  recipes-security/             # Sudoers policy (wheel group, passwordless)
  wic/                          # WIC disk image layout
```

## User Setup

- Root is locked (`password = '!'`)
- Default user: `wilhelmos` (password: `wilhelmos`), member of `wheel` group
- Wheel group has passwordless sudo via `/etc/sudoers.d/10-wheel`

## Backlog

See [TODO.md](TODO.md) for:
- General improvements (recipe metadata, Makefile, kernel config)
- ED-109A certification roadmap (evidence artifacts, hardening, monitoring, documentation)

## Conventions

- Recipes use Yocto Kirkstone syntax (`:append`, `:remove`, not `_append`)
- Recipe overrides go in `.bbappend` files with `%` version wildcard
- KAS config variable: `KAS_FILE` (defaults to `kas/qemu-kirkstone.yaml`)
