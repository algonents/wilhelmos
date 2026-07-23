# WilhelmOS Design & Phased Roadmap

Status: living document. Last updated: 2026-07-23.

## 1. Purpose & positioning

WilhelmOS is a minimal, reproducible, hardened Linux platform for **AL3–AL5
CNS/ATM ground equipment** (e.g. Controller Working Position displays),
positioned as **COTS software under ED-109A Section 12.4**. The system
integrator (applicant) builds their assurance case around WilhelmOS; the
platform's job is to shrink that burden by shipping:

- a pinned, reproducible Yocto build with SBOM output (Section 7 evidence),
- a restricted, hardened configuration (Section 12.4.11
  restriction-of-functionality argument),
- safety-monitoring hooks via systemd (Section 2.4.3 objectives),
- eventually, a COTS Software Integrity Assurance Case template.

WilhelmOS targets two workload classes:

1. **CWP display (kiosk)** — the primary product mode: a graphical kiosk
   running the `sky_guard_client` ATM situation display.
2. **SWIM-style services (headless)** — ATM web services in the SWIM
   (System Wide Information Management) sense: e.g. `sky_guard_server`,
   surveillance data distribution, information services. These must run
   **resiliently** (automatic restart on failure, hang detection) and
   **contained** (a misbehaving service must not starve higher-priority
   ones). See §5 for the service resilience & resource control design.

A TTY mode serves headless/maintenance use for either class.

## 2. Phase 0 — Baseline (this iteration, DONE)

Everything later phases claim as evidence hangs off a fixed, auditable
baseline. Phase 0 delivered:

- **Pinned upstreams** in `kas/qemu-kirkstone.yaml`:
  - poky @ `393064579dfdd0ed2d4ed4c27d238d7d2292c08b` (kirkstone)
  - meta-openembedded @ `ce8539c941f6fcbecaca4d16640ac105c0595589` (kirkstone)
- **SBOM**: `create-spdx` inherited in the distro conf; SPDX archive lands in
  `tmp/deploy/images/`.
- **Reproducibility**: `BUILD_REPRODUCIBLE_BINARIES = "1"` asserted;
  `buildhistory` (with commits) for manifest regression evidence.
- **Kernel hardening fragment** (`hardening.cfg`): KASLR, strong stack
  protector, strict RWX, hardened usercopy/slab, `/dev/mem` removed,
  lockdown + yama LSMs compiled in (inert — see §3).
- **Persistent journald** with size caps; `VOLATILE_LOG_DIR = "no"`.
- **Recipe hygiene**: correct licenses (Terminus font is OFL-1.1), full
  metadata, layer dependencies, fetcher-based installs.
- **Debug/production wic split**: `wilhelmos-efi.wks` (production) vs
  `wilhelmos-efi-debug.wks` (verbose boot logging, `kas/debug.yaml` overlay).

## 3. Known constraints & flagged items

### Kirkstone is EOL (April 2026)
The pinned commits freeze the final kirkstone LTS state. A migration to
**scarthgap (5.0 LTS)** is a near-term roadmap item. Expected impact:
kernel 5.15 → 6.6 (re-validate `hardening.cfg` options), `create-spdx`
moves toward SPDX 3.0 output, `LAYERSERIES_COMPAT` and kas layer-compat
bumps. Running an EOL base contradicts the "maintained COTS" story, so this
should precede any certification engagement.

### Dev-only default credential
`wilhelmos.conf` bakes in user `wilhelmos` with a known password
(`wilhelmos`) for development. **Production images must override
`EXTRA_USERS_PARAMS`** (and review getty exposure). Root is locked. Sudo for
the wheel group deliberately **requires a password** — this is the intended
policy; do not add NOPASSWD.

### Lockdown LSM is compiled in but not active
`lockdown=integrity` is deliberately **not** on the kernel cmdline yet.
Lockdown without verified boot (dm-verity/secure boot, Phase 2) provides
little real protection while complicating development; activating it is part
of the Phase 2 integrity design. Module signing is likewise deferred to
Phase 2 (key management belongs with the secure-boot design).

## 4. Phase 1 — Graphical kiosk mode (next)

Today the kiosk mode exists only as documentation; **no recipes implement
it**.

### Compositor decision: cage (decided)

A compositor is required, not optional: **GLFW has no direct DRM/KMS
backend** — it only supports X11 and Wayland. Running without a compositor
would mean rewriting wilhelm_renderer's windowing on raw EGL/GBM, which is
engine work we don't want. So the production stack is:

```
systemd → cage → sky_guard_client (GLFW-Wayland) → Mesa → DRM/KMS → display
```

**The production compositor is `cage`** (wlroots-based kiosk compositor),
packaged via our own recipe. Rationale:

1. **Certification scope (ED-109A §12.4.11 restriction of functionality).**
   cage is a few thousand lines with exactly one capability: run a single
   application fullscreen. Weston is the full reference compositor —
   multiple shells, RDP backend, screen sharing, config surface — of which
   a kiosk uses a sliver, but all of it ships and all of it must be
   accounted for in the COTS argument. "The compositor is architecturally
   incapable of doing anything but display the CWP application" is the
   assurance sentence we want.
2. **The kiosk constraint is architectural, not configured.** Weston's
   kiosk mode is policy in a config file; misconfiguration or changed
   defaults can surface unintended behavior. cage has nothing to
   misconfigure — the invariant cannot be configured away.
3. **Footprint** — smallest binary and dependency set, consistent with the
   minimal-platform positioning.

Accepted cost: we own the cage (+ wlroots, if not available in our layers)
recipes — version tracking, CVE watching, and keeping wlroots compatible
with our wayland/libinput versions. This is deliberate: the maintenance
cost buys the §12.4.11 argument.

**Bring-up strategy:** initial stack bring-up may use Weston's kiosk-shell
(already in oe-core, exercised daily by the Yocto ecosystem) as a scaffold
to de-risk the kernel DRM config, Mesa, and GLFW-Wayland work — so failures
during bring-up are attributable to our stack, not the compositor recipe.
Weston is a temporary scaffold only: it must not ship in the production
image, and the phase is not complete until sky_guard_client runs under
cage. The cage/wlroots recipe work is easier after the scarthgap migration
(§3), which is another reason to sequence that migration early.

### Target GPU: integrated graphics (decided)

**Production hardware targets integrated Intel or AMD graphics (iGPU).**
Integrated does not mean software rendering: an iGPU is a full GPU with
hardware OpenGL, driven by in-tree kernel drivers (i915/xe for Intel,
amdgpu for AMD) and Mesa's native userspace drivers (iris/radeonsi) —
hardware acceleration through exactly the DRM/KMS + Mesa stack this design
already assumes, with no special-casing.

Rationale:

1. **Fully open driver stack** — in-tree kernel drivers + Mesa. No binary
   blob, no out-of-tree kernel module; the SBOM covers the entire graphics
   stack and the kernel hardening/module story stays intact.
2. **Certifiably boring** — i915/amdgpu + Mesa are among the most widely
   deployed graphics drivers in existence (service-experience argument,
   ED-109A §12.3.4).
3. **Adequate performance** — a modern iGPU drives multiple 4K displays;
   the CWP workload (2D OpenGL: map, tracks, symbology, ImGui chrome) is
   trivial against its capability.

Discrete NVIDIA GPUs are explicitly **not** targeted: the proprietary
driver is a closed blob with an out-of-tree kernel module (breaks the SBOM,
kernel-hardening, and restriction-of-functionality arguments), nouveau
cannot reclock modern cards, and the open NVK path needs a far newer Mesa
than our LTS carries. If a future deployment mandates NVIDIA hardware, that
is a major design decision requiring its own section here — not a bring-up
detail.

**Hardware selection guidance**: prefer a *mature* iGPU generation over the
newest silicon — bleeding-edge iGPUs need a newer kernel/Mesa than an LTS
Yocto branch carries. Concrete example: an Arrow Lake-S iGPU (Core Ultra,
2024) needs roughly kernel ≥ 6.10 + Mesa 24 — unsupported on kirkstone
(5.15/Mesa 22) and borderline even on scarthgap (6.6). An 8th–12th gen
Intel UHD or an established AMD APU works with LTS stacks out of the box.
Validate the exact iGPU SKU against the kernel/Mesa versions of the Yocto
release in use *before* committing to hardware, and size the SKU by
display-output requirements (monitor count × resolution per CWP position).

### Scope

- Kernel: DRM/KMS config fragment (`CONFIG_DRM`, `CONFIG_DRM_VIRTIO_GPU`
  for QEMU; `CONFIG_DRM_I915` / `CONFIG_DRM_AMDGPU` per target iGPU for
  bare metal).
- `DISTRO_FEATURES += "opengl wayland"`.
- Userspace: Mesa (GL/EGL/GBM), cage (+ wlroots recipe if needed), GLFW
  (meta-oe), freetype/fontconfig, B612Mono font package (aviation display
  font, same recipe pattern as the Terminus font).
- `sky_guard_client` recipe (cross-compiled; depends on wilhelm_renderer +
  wilhelm_renderer_imgui + libasterix) — expected to be the largest effort.
- Boot-mode wiring: systemd target per mode (`kiosk.target` /
  `maintenance.target`), cage service auto-launching sky_guard_client with
  restart policy (per §5), TTY autologin for maintenance mode, shell kept
  on tty2.
- Image-size and boot-time evaluation after the GPU stack lands.

### Test strategy (QEMU-first)

The full chain — systemd → cage → GLFW → Mesa → DRM/KMS — is testable in
QEMU with a virtio-gpu device: the guest runs real Mesa against a real DRM
device, identical code path to hardware.

- Interactive: `runqemu qemux86-64 gl` (virgl-accelerated OpenGL, window on
  the host); llvmpipe software rendering as fallback for headless hosts.
- Automated (feeds Phase 3 CI): headless QEMU + QMP `screendump` for
  golden-image regression tests of the rendered display.
- Not covered by QEMU — requires target hardware: real GPU driver quirks
  (i915/amdgpu), rendering performance, multi-monitor/EDID behavior.

## 5. Service resilience & resource control (SWIM services)

WilhelmOS must run ATM information services (SWIM-style web services) so
that a failed or runaway service is detected, restarted, and prevented from
degrading anything more critical. All of this is systemd/cgroup-v2 policy —
no custom supervisor is needed, which keeps the COTS argument clean.

### Resilience (ED-109A §2.4.3 — safety monitoring)

Per-service unit policy, shipped as a WilhelmOS template drop-in:

- **Automatic restart**: `Restart=on-failure` (or `always` for stateless
  services), `RestartSec=` with backoff.
- **Hang detection**: `WatchdogSec=` + `sd_notify` heartbeats for services
  that support it — catches livelock, not just crashes.
- **Escalation**: `StartLimitIntervalSec`/`StartLimitBurst` so a
  crash-looping service stops flapping, with `OnFailure=` hooks for an
  alerting/degraded-mode unit; ultimately `FailureAction=` can reboot into
  a known-good state (ties into the Phase 2 A/B design).
- **Boot-level supervision**: the hardware watchdog chain
  (`RuntimeWatchdogSec`, Phase 2) covers systemd itself.

### Prioritization & containment (ED-109A §2.4.1 — partitioning)

A fixed slice hierarchy so criticality is explicit and resource caps are
structural rather than per-unit ad hoc:

```
-.slice
├── wilhelmos-critical.slice     # CWP display / primary service
│     CPUWeight high, MemoryMin reserved, IO weight high
├── wilhelmos-services.slice     # SWIM services
│     CPUWeight normal, MemoryHigh/MemoryMax caps, TasksMax
└── system.slice                 # everything else (journald, timesyncd, …)
```

- **Priority**: `CPUWeight=`/`Nice=` for proportional share;
  `CPUSchedulingPolicy=fifo|rr` reserved for genuinely latency-critical
  processes (display rendering), used sparingly.
- **Capping runaway processes**: `MemoryHigh=`/`MemoryMax=` (throttle, then
  OOM-kill only the offender — `OOMPolicy=kill` scoped to the service),
  `CPUQuota=` where a hard ceiling is wanted, `TasksMax=` against fork
  bombs, `IPAddressDeny=`/`IPAddressAllow=` for network scoping.
- **Sandboxing** (restriction-of-functionality, §12.4.11): service units
  get `NoNewPrivileges=`, `ProtectSystem=strict`, `PrivateTmp=`,
  `CapabilityBoundingSet=`, `DynamicUser=` where state permits.

The resource-partitioning story directly supports the ED-109A §2.4.1
argument that components in different slices can be assigned different ALs.

**Deliverables & sequencing**: the slice hierarchy and a hardened service
unit template can land as soon as the first real service unit exists
(Phase 1, alongside the sky_guard units); watchdog escalation and
`FailureAction` reboot semantics belong with Phase 2's A/B design. A
QEMU-based fault-injection test (kill/hang/mem-hog a demo service, assert
restart + containment) becomes part of the Phase 3 verification evidence.

## 6. Phase 2 — Partition, update & integrity design

These items are **coupled** and are deferred deliberately — designing them
piecemeal would force rework:

- **A/B rootfs scheme** (hot-swap / rollback, ED-109A 2.5.4) dictates the
  wic layout and update tooling.
- **Read-only rootfs** requires deciding where writable state lives
  (separate `/var` partition vs overlays) — constrained by the A/B layout.
- **dm-verity / IMA-EVM** seal the rootfs and require the final partition
  map; only then does activating `lockdown=integrity` (and module signing)
  deliver real guarantees.
- **Hardware watchdog** (`RuntimeWatchdogSec`) and **service supervision**
  policies should be designed around the real sky_guard unit files from
  Phase 1, not hypothetical ones.
- systemd `PACKAGECONFIG` audit / service stripping for the final package
  set.

Sequencing: Phase 1 fixes the package/service set → Phase 2 freezes the
partition and integrity architecture around it.

## 7. Phase 3 — ED-109A evidence package

- PSAA template (Section 11.1) mapping WilhelmOS artifacts to objectives.
- Software Configuration Index (Section 11.16) generated from the pinned
  kas config + buildhistory + SPDX outputs.
- COTS Software Integrity Assurance Case template (Section 12.4.11).
- Mapping table: WilhelmOS evidence → Annex A objectives (Tables A-7/A-8).
- CI: QEMU boot-to-login test, image-size and manifest regression checks,
  `kernel_configcheck` gate — turning the Phase 0 verification steps into
  repeatable automated evidence.

## 8. Sequencing rationale

Hygiene and reproducibility came first because every later claim — "this
image is hardened", "this service set is minimal", "this binary matches this
source" — is only evidence if it refers to a build that can be reproduced
bit-for-bit from pinned inputs. Hardening that would be invalidated by the
kiosk stack (service stripping, partition/integrity design) waits until the
real payload exists; hardening that survives it (kernel flags, persistent
logging, credential policy) landed in Phase 0.
