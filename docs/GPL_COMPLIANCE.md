# GPL Compliance & the Sealed-Image Question

Why GPLv3 matters specifically to WilhelmOS, the audit of what actually
ships, and the remediation plan. Companion to
[CERTIFICATION.md](CERTIFICATION.md) (the license evidence is part of
the per-release pack) and a **prerequisite for Phase 2**
(DESIGN.md §6 — secure boot, dm-verity, signed A/B images).

## Why this matters: anti-Tivoization

GPLv3 §6 requires that when covered software ships in a locked-down
"User Product" — a device that only runs vendor-signed code — the
distributor must provide the **Installation Information** (keys,
procedures) needed to install and run *modified* versions on that
device. This clause exists because TiVo shipped GPLv2 Linux on
signature-locked hardware: source was published, but modified builds
would not boot. GPLv2 has no such clause (which is why the Linux
kernel itself is unaffected).

Phase 2 builds exactly the kind of device §6 regulates. Today, with
unsealed images, GPLv3 content is an ordinary compliance footnote; the
day the bootloader enforces signatures it becomes a design constraint.
There is a scope argument that B2B ATM ground equipment sold to ANSPs
is not a "User Product" (a consumer-product term) — but WilhelmOS does
not lean on that argument; the policy below makes it moot.

## Audit (2026-08-02)

Method: Yocto's per-image license manifests
(`build/tmp/deploy/licenses/<machine>/<image>-*/license.manifest`),
which list every installed package with its SPDX license expression —
the same machinery that feeds the SBOM. Audited: the bare-metal
`wilhelmos-image-kiosk` (740 packages) and `wilhelmos-image-spa`
(1047 packages), current master. Eleven packages match GPL-3; they
fall into three categories:

### 1. Exception-covered — no GPLv3 obligations (keep)

| Package | License | Why it is fine |
|---|---|---|
| libgcc, libstdc++, libatomic | `GPL-3.0-with-GCC-exception` | The GCC Runtime Library Exception exists precisely so any program can link the compiler runtime without inheriting GPLv3 terms — no Installation Information duty |
| libglvnd | `MIT & BSD-1-Clause & BSD-3-Clause & GPL-3.0-with-autoconf-exception` | The GPLv3 fragment is an autoconf build script under its exception; the shipped code is MIT/BSD |

### 2. Dual-licensed — elect the GPL-2.0 terms (record election)

| Package | License expression |
|---|---|
| gmp | `GPL-2.0-or-later \| LGPL-3.0-or-later` |
| libelf | `GPL-2.0-or-later \| LGPL-3.0-or-later` |
| libidn2 | `(GPL-2.0-or-later \| LGPL-3.0-only) & Unicode-DFS-2016` |
| libunistring | `LGPL-3.0-or-later \| GPL-2.0-or-later` |
| nettle | `LGPL-3.0-or-later \| GPL-2.0-or-later` |

The `|` grants the distributor the choice of terms. **WilhelmOS elects
the GPL-2.0 terms for all five** (this document records that election;
it also lands in the per-release evidence pack). Under GPL-2.0 no
Installation Information duty exists.

### 3. Genuinely GPLv3-only — remove before Phase 2

| Package | License | Remediation |
|---|---|---|
| nano | `GPL-3.0-only` | Dev-convenience editor only; drop from production images (BusyBox `vi` remains). May stay in dev images if those are never sealed |
| kbd-keymaps-pine | `GPL-3.0-or-later` | Console keymap data; narrow the keymap set or source the needed layout (fr_CH) from a non-GPLv3 package |

**Bottom line: the shipped platform is two trivially replaceable
packages away from GPLv3-clean — in both profiles, browser included** —
a direct dividend of the BusyBox-first minimal image policy.

## Remediation plan (Phase 2 kickoff, ~1 h)

1. Remove/replace the two category-3 packages in production images.
2. Enforce permanently in the distro config:
   `INCOMPATIBLE_LICENSE = "GPL-3.0-only GPL-3.0-or-later LGPL-3.0-only LGPL-3.0-or-later AGPL-3.0-only AGPL-3.0-or-later"`
   — exception-licensed packages (category 1) are distinct SPDX
   identifiers and stay allowed; category-2 packages need their
   election expressed to the build (per-recipe license election) so
   they continue to build under the exclusion.
3. Record the elections and the exception rationale in the release
   evidence pack (CERTIFICATION.md's SBOM section).
4. Optional CI gate: fail any build whose license manifest matches
   `GPL-3` outside the documented exceptions.

Until then, nothing is blocked: images are unsealed and ordinary GPL
source-availability compliance applies (Yocto's standard mechanisms
cover it).
