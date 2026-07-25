# WilhelmOS Design & Phased Roadmap

Status: living document. Last updated: 2026-07-25.

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

- **Pinned upstreams** (originally kirkstone: poky @ `3930645`,
  meta-openembedded @ `ce8539c`; superseded by the wrynose migration —
  current pins live in `kas/qemu-wrynose.yaml`, see §3).
- **SBOM**: `create-spdx` inherited in the distro conf; SPDX archive lands in
  `tmp/deploy/images/`.
- **Reproducibility**: `BUILD_REPRODUCIBLE_BINARIES = "1"` asserted;
  `buildhistory` (with commits) for manifest regression evidence.
- **Kernel hardening fragment** (`hardening.cfg`): KASLR, strong stack
  protector, strict RWX, hardened usercopy/slab, `/dev/mem` removed,
  lockdown + yama LSMs compiled in (inert — see §3).
- **Persistent journald** with size caps; /var/log kept on real storage
  (`VOLATILE_LOG_DIR` on kirkstone, fs-perms table removal since wrynose).
- **Recipe hygiene**: correct licenses (Terminus font is OFL-1.1), full
  metadata, layer dependencies, fetcher-based installs.
- **Debug/production wic split**: `wilhelmos-efi.wks` (production) vs
  `wilhelmos-efi-debug.wks` (verbose boot logging, `kas/debug.yaml` overlay).

## 3. Known constraints & flagged items

### Yocto base: wrynose 6.0 LTS (migrated 2026-07-23)
WilhelmOS started on kirkstone (4.0 LTS, EOL April 2026) and migrated
directly to **wrynose (6.0 LTS, supported until April 2030)**, skipping
scarthgap deliberately: one migration instead of two before the
certification timeline matures, four years of runway, and a kernel/Mesa
new enough for recent iGPUs (scarthgap's 6.6 kernel predates e.g. Arrow
Lake graphics). Wrynose is young — pins should be advanced as point
releases land.

Notable changes absorbed in the migration: Yocto 5.3+ publishes no poky
combined repo (the build uses openembedded-core + bitbake + meta-yocto as
separate pinned repos); `INIT_MANAGER = "systemd"` replaces manual
feature juggling and brings `usrmerge`; `VOLATILE_LOG_DIR` became a
fs-perms-table removal; recipes unpack local files to `UNPACKDIR`
(`S = "${WORKDIR}"` unsupported); layer wks files moved to `files/wic/`.
Current pins live in `kas/qemu-wrynose.yaml`.

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

## 4. Phase 1 — Graphical kiosk mode

**Status: stack validation DONE (2026-07-24).** Phase 1 was deliberately
scoped to validating that *any application built with wilhelm_renderer +
wilhelm_renderer_imgui runs seamlessly on WilhelmOS* — packaging
`sky_guard_client` itself is deferred until the stack is proven. The
validation vehicle is `wilhelm-renderer-demo`: the imgui `demo_kiosk`
example packaged as a recipe and run fullscreen under cage
(`wilhelmos-image-kiosk`, boots to `graphical.target`, maintenance getty on
tty2). Verified end to end in QEMU: cage + seatd active, demo rendering
(QMP screendump golden check), no crashes or restarts.

What validation delivered:

- **Recipes** (meta-wilhelmos/recipes-graphics/): wlroots 0.19.3, cage
  (post-0.2.1 master pin for wlroots-0.19, plus a backported guard for
  pre-commit fullscreen requests), `wilhelmos-kiosk-session` (systemd
  service, dedicated `kiosk` user), `wilhelm-renderer-demo` (cargo recipe
  building the example from git branches with crates.io deps vendored).
- **Session model: seatd, not logind.** PAM is not in DISTRO_FEATURES; the
  cage service runs as a dedicated system user with `LIBSEAT_BACKEND=seatd`.
  Revisit only if a logind session becomes necessary.
- **Upstream crate changes** (branch `feat/kiosk-validation`, to be merged +
  published): `Window::new_fullscreen` API, `GLRENDERER_BUILD_X11` switch
  (Wayland-only builds need no X11 headers), `GLRENDERER_LINK_GL` switch
  (glad loads GL at runtime; no libGL link on GLX-less Mesa).
- **x11 opted out distro-wide** (`DISTRO_FEATURES_OPTED_OUT`): no GLX/X11
  code paths in the image — restriction of functionality, §12.4.11. GLVND
  is enabled so runtime GL loaders find the vendor-neutral
  libEGL/libOpenGL dispatch libraries on the GLX-less system.

**Hardware bring-up (in progress):** the kiosk image also builds for
`MACHINE = genericx86-64` via the `kas/hw.yaml` overlay (same x86-64-v3
tune, so userspace is shared with the QEMU build). `gpu.cfg` adds
i915/xe/amdgpu as modules plus a SimpleDRM/UEFI-GOP KMS fallback; Intel GPU
firmware is installed machine-conditionally. The full UEFI → systemd-boot →
GPT → `root=PARTLABEL` boot chain is validated in QEMU via OVMF (9/9
kiosk checks through the wic path). Deployment is USB media: prefer
`bmaptool copy` of the `.wic.zst` (the build emits `.wic.bmap` — writes
only mapped blocks and verifies checksums; `dd` of the raw `.wic` works
but is far slower and unverified). Use a quality USB 3 stick — cheap
flash sustains ~1-2 MB/s. Secure Boot must be disabled until the Phase 2
signing design.

**Known-good flash procedure (validated 2026-07-25).** A USB
mass-storage write that straddles a host suspend/resume cycle wedges the
stick (one write request stuck in-flight forever, dd unkillable in D
state, ~nothing written) — and a desktop host that *fails* to suspend
(e.g. an NVIDIA driver returning -5) will retry in a loop, breaking
every flash attempt. The sequence that works:

```
# 1. one-time per session: stop the host from ever suspending mid-write
sudo systemctl mask --now sleep.target suspend.target hybrid-sleep.target hibernate.target
# 2. no partition of the stick may be mounted (desktops automount!)
lsblk /dev/sdX    # unmount anything mounted, e.g. udisksctl unmount -b /dev/sdX1
# 3. decompress and flash with direct I/O (progress = real device writes)
zstd -f -d wilhelmos-image-kiosk-genericx86-64.rootfs.wic.zst -o flash.wic
sudo dd if=flash.wic of=/dev/sdX bs=4M status=progress oflag=direct conv=fsync
```

`oflag=direct` matters twice over: dd's progress shows true device
throughput (buffered dd reports GB/s into the page cache, then "hangs"
in the final fsync), and a stall is visible within seconds instead of
after minutes. Expect bursty rates on cheap sticks (fast until the SLC
cache fills at a few hundred MB, then the true sustained rate).
`bmaptool copy` works equally well once suspend is masked — the suspend
loop, not the tool, was the failure cause on the build host.

**First bare-metal boot (Arrow Lake workstation, 2026-07-25).** Driver
question answered: the Core Ultra 7 265K iGPU (device 8086:7d67) binds
**i915** (not xe) on kernel 6.18, display version 14 (Meteor Lake-class
display block); Mesa 26.0.5 iris reports "Intel(R) Graphics (ARL)" with
GLES 3.2 — full hardware acceleration, all GuC/HuC/DMC firmware loaded.
The boot itself failed its purpose, though: cage showed only a cursor on
a black screen, no demo app. Post-mortem from the persistent journal:

1. SimpleDRM registers `card0` (the UEFI-GOP firmware framebuffer)
   within the first second of boot. i915 is a **module**, and on the
   slow USB stick udev coldplug + firmware loading delayed its probe to
   ~150s after kernel start.
2. `cage-kiosk.service` started 12s *before* i915 bound. wlroots
   enumerated one GPU (SimpleDRM), found no hardware EGL on it, refused
   software GL ("Software rendering detected"), and fell back to its
   pixman renderer — hence the software-composited cursor.
3. SimpleDRM exposes no **render node** (`/dev/dri/renderD*`), so the
   demo client couldn't create any EGL device either ("failed to create
   dri2 screen") — no triangle.
4. When i915 finally bound, wlroots hotplugged `card1`, modeset the
   monitor (proving the hardware path worked), but the session was
   already built around the pixman/SimpleDRM renderer and every atomic
   commit on the new output failed with `Device or resource busy` —
   wedged until reboot. A wedged cage also swallows VT-switch keys, so
   the maintenance console appeared dead even though `getty@tty2` was
   healthy.

Fixes wired back into the tree:

- `cage-kiosk.service` gains a second `ExecStartPre` gate (mirroring the
  seatd-socket wait): poll up to 60s for a `/dev/dri/renderD*` node —
  only native GPU drivers create render nodes, so this is a reliable
  "real GPU is ready" signal — then proceed anyway, preserving the
  SimpleDRM fallback on unsupported GPUs. `WLR_RENDERER_ALLOW_SOFTWARE=1`
  is set so that fallback actually yields a llvmpipe session instead of
  wlroots refusing to start (inert when a hardware GL device exists).
- `gpu.cfg` adds `CONFIG_INTEL_MEI`/`_ME`/`_GSC_PROXY`: the journal
  showed `GT1: GSC proxy handler failed to init` caused by the missing
  MEI GSC proxy component (content-protection/firmware handshake path —
  display and GL are unaffected, but the dmesg *ERROR* should be clean).

**Re-test (same hardware, 2026-07-25): validated.** With the fixes above
the machine boots straight into the fullscreen `wilhelm-imgui-demo`
proof-of-concept app (hardware GL on i915; sky_guard_client itself does
not exist yet), and Ctrl-Alt-F2 switches to the maintenance getty as
designed. The kiosk boot path — `systemd → cage → client → OpenGL →
DRM/KMS` — is confirmed end-to-end on bare metal.

Debugging technique worth keeping: journald is persistent on WilhelmOS,
so a failed bare-metal boot can be analyzed offline by mounting the USB
stick read-only on the build host and pointing journalctl at it
(`journalctl -D <mount>/var/log/journal --list-boots`, then `-b0 -u
cage-kiosk` / `-k`). No serial cable or working console needed. Known
log noise on this hardware: `serial-getty@ttyS0` restart-loops because
the board has no usable UART (`SERIAL_CONSOLES` comes from oe-core's
x86-base.inc); disabling serial consoles in production variants is
already on the TODO backlog.

Remaining for full kiosk productization (next iterations): sky_guard_client
recipe, B612Mono font package, kiosk/maintenance systemd target switching,
the customer composition contracts (`KIOSK_APP` image variable +
`/usr/libexec/kiosk-app` session exec path — see §7),
image-size/boot-time evaluation, a pinned production hardware machine
config once CWP hardware is selected.

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
cage. The cage/wlroots recipe work benefits from the wrynose base (§3) and
its newer wayland/libinput stack.

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
2024) needs roughly kernel ≥ 6.10 + Mesa 24 — out of reach for older LTS
branches (kirkstone: 5.15/Mesa 22) but covered by our wrynose base (§3).
An 8th–12th gen Intel UHD or an established AMD APU works with any LTS
stack out of the box.
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
Everything in this section applies unchanged whether a service runs as a
plain binary or as a Quadlet-managed container under the server-class
update model (§6): Quadlet renders containers as native systemd units, so
they receive the same restart, watchdog and resource policies.

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
  (separate `/var` partition vs overlays) — constrained by the A/B layout,
  and on the server class also sized for container image storage (see the
  server-class update decision below).
- **dm-verity / IMA-EVM** seal the rootfs and require the final partition
  map; only then does activating `lockdown=integrity` (and module signing)
  deliver real guarantees.
- **Hardware watchdog** (`RuntimeWatchdogSec`) and **service supervision**
  policies should be designed around the real sky_guard unit files from
  Phase 1, not hypothetical ones.
- systemd `PACKAGECONFIG` audit / service stripping for the final package
  set.

### Update strategy — invariant principle (all workload classes)

Deployed devices are updated through **atomic, image-based updates only** —
never on-device package management, and never in-place replacement of
individual files. A package manager (or a hand-replaced executable) on the
target means a mutable rootfs, and "what exactly is running on that box"
stops having a crisp answer; the SBOM/baseline evidence chain collapses.
Every deployed unit of change must be a complete, versioned, signed,
reproducible, SBOM'd artifact. What differs per workload class (§1) is
the *granularity* of that unit — decided separately below for the CWP
equipment class and the SWIM server class.

### CWP equipment class: monolithic baseline (decided 2026-07-25)

*Supersedes the "two independent update paths" decision of 2026-07-23;
the superseded design is kept below as the documented fallback.*

For the CWP equipment class there is exactly **one update path**: platform
and application ship as a single monolithic image. A new application version
*is* a new equipment software baseline, delivered as a complete signed
image into the **A/B rootfs slots** (write the inactive slot, switch,
reboot; failed boot rolls back automatically — ED-109A §2.5.4
cutover/hot-swap). This holds for every change, from an LTS kernel
migration down to an emergency one-line application fix.

**Rationale — the equipment concept.** An ATM equipment (a "constituent"
in EU-regulation terms) is a unit of accountability: a
configuration-controlled whole — hardware + OS + application — with a
declared intended function, performance and interfaces, for which one
party stands behind the complete stack. The ANSP's safety case references
*equipment X at baseline Y*, and that reference is only meaningful if the
baseline pins everything that affects behavior. The deployed unit of
configuration is therefore the whole software load; monolithic updates
are simply the update-mechanism expression of what an equipment *is*.
This is the established practice in every comparable regulated domain:
avionics (ARINC 665 complete loads, one part number per load), medical
devices (whole-device software releases), network equipment (single
versioned firmware images), and — at consumer scale — ChromeOS (A/B
full-image autoupdate with verified boot, the origin of dm-verity).
Split update models (Android system-image + APKs, balenaOS/Torizon
host-OS + app containers) exist to serve a different problem: platform
and applications owned by *different release authorities* that cannot
coordinate. WilhelmOS integrators compose application and platform at
build time and ship one certified appliance — that problem does not
exist here (see §7: split at build time via layers, monolithic at
deploy time).

**What this buys, concretely:**

- One artifact, one version string, one SBOM, one signature, one slot
  pair, one updater configuration. "What is running on that box" has a
  one-line answer.
- Phase 2 collapses: two rootfs slots with one dm-verity tree each — no
  app partition pair, no second slot state machine, no second signing
  chain, no app-slot mount/verify ordering at boot. The application
  binary sits *under* the same verity seal as the platform.
- You test what you ship: every release is the exact integration-tested
  artifact. No (platform × application) compatibility matrix, no
  platform-ABI contract to author, version and enforce.

**Independence of evidence replaces independence of partitions.** The
property the two-path design protected — an application fix must not
reopen the platform's configuration baseline — is retained, but carried
by reproducibility instead of by the partition map: when a release
changes only the application, buildhistory and the per-package SBOM show
every platform package bit-identical between the two images. Change
impact analysis becomes a manifest diff ("1 package of ~300 changed,
hashes attached") — the same "demonstrably unchanged" claim, with
easier-to-audit evidence.

**Failure modes of the rejected install path** (recorded so the decision
stays auditable): independently-updated applications reintroduce ABI
drift against platform libraries (glibc/Mesa/wayland versions — hence
the contract + test matrix); a second A/B state machine that must be
exactly as robust as the first; an ill-defined application-level health
signal for rollback (the worst kiosk failure is *plausible but wrong*
display output, which no health check catches); a boot-time
mount/verify/ordering contract for the app slot (compare the
SimpleDRM-vs-i915 race in §4 — same bug class); dual signing chains
with anti-downgrade policy in two places; bundle scope creep quietly
eroding the "platform unchanged" claim; and fleet version skew
fragmenting the §12.3.4 service-experience evidence across (platform,
app) combinations.

**Costs accepted:** every application release redelivers the full image
(hundreds of MB rather than a ~20 MB bundle) — irrelevant for
maintenance-window delivery via USB or site network a few times a year;
and every release re-exercises the full boot chain — mitigated by A/B
rollback. These are the revisit triggers: update cadence far beyond a
few per year, delivery over constrained links to many unattended sites,
or multiple applications with genuinely independent release authorities
would reopen this decision. (The last trigger *has* materialized — for
the SWIM server class, not the CWP: hundreds of services with
independent authors and cadences. It is handled by splitting the
decision per workload class — see the server-class section below — not
by reopening the CWP decision, whose premises are unchanged.)

**Reversibility hedge (decided):** the Phase 2 partition map **reserves
an unused application slot pair in the GPT** anyway. A/B updates write
partitions but never repartition; without reserved slots, a later
migration to split updates would mean re-provisioning every deployed
device on site. With them, the migration becomes a software/process
change deliverable through the normal update path. Cost: disk space on
hardware where disk is free.

**Fallback (superseded 2026-07-25): two independent update paths** —
platform image into the A/B rootfs slots; application as its own small,
signed, versioned bundle on a dedicated partition/slot pair with its own
A/B + rollback semantics, plus an explicitly-managed platform-ABI
compatibility contract recorded per release (compositor protocol,
runtime libs, systemd interface). Retained as the documented alternative
should the revisit triggers above materialize.

Candidate tooling: **RAUC** (first choice — native full-image A/B
semantics, X.509-signed dm-verity bundles, systemd-boot boot-counting
support, Yocto integration via meta-rauc; an application slot class can
be added to its slot configuration later if the fallback is ever
activated) or swupdate. Decision falls with the partition-layout design
since bundle format and slot map are coupled.

### SWIM server class: sealed platform + containerized services (decided 2026-07-25)

The monolithic-baseline rationale does not transfer to the server class.
A SWIM estate is potentially **hundreds of services with independent
authors and release cadences** — exactly the different-release-authorities
problem the CWP analysis identified as the legitimate home of split
update models. Forcing every one-service fix through a full node
image-and-reboot cycle would put every team's release through one build
authority's pipeline; the coordination cost grows with the service count.

The requirement is deliberately *not* "update a single executable" —
in-place binary replacement is forbidden by the invariant principle
above. The requirement is: **update a single service as its own small,
atomic, signed, versioned unit, without touching the platform baseline.**

**Decision:** on the server class, the WilhelmOS platform image keeps the
identical machinery as the CWP (A/B rootfs slots, RAUC, dm-verity seal,
read-only rootfs) and additionally carries a minimal container runtime;
each SWIM service is an **OCI container image** — independently built,
versioned, signed, **pinned by digest** — stored on the writable data
partition and updated per-service with no reboot and no write to the
platform partitions. The two workload classes differ only in what rides
on top of the same sealed platform.

Runtime choice: **podman + Quadlet, not Kubernetes.** Quadlet renders
each container as a native systemd unit, so the entire §5 design —
restart/hang-detection policies, watchdog, `CPUWeight`/`MemoryMax`
containment, priority protection — applies to containerized services
verbatim. Podman is daemonless; there is no orchestrator, no control
plane, and the certification-surface delta over the already-shipped
systemd machinery is small. (Same selection logic as cage-over-Weston:
the minimal component that does exactly the job.)

What this preserves and strengthens:

- **Independence of partitions returns at service granularity** — a
  service update physically does not write the platform partitions; the
  platform baseline is untouched, not merely demonstrably-unchanged.
- **§2.4.1 partitioning becomes enforceable per service**: containers
  give each service its own failure domain, resource caps and update
  cadence, which is what makes per-component AL assignment concrete
  (an AL5 and an AL3 service can share a node).
- **Node baseline stays crisp**: platform image version + the set of
  service image digests, all machine-readable — the fleet CM record is
  a short list of hashes.
- Per-service SBOMs travel in the service images; the platform SBOM is
  unchanged by service releases.

Costs accepted: podman and its dependencies join the platform baseline
(pinned, SBOM'd, hardened like everything else); a service-image
signing/verification chain (digest pinning + signature policy) must be
operated; and the platform/service interface (runtime version, network
policy, volume contracts) becomes a versioned compatibility surface — a
narrower cousin of the platform-ABI contract the CWP decision deleted.

**Documented alternative (not selected): monolithic redundant nodes.**
Keep CWP-style monolithic node images and exploit server redundancy:
services deploy N+M for availability anyway, so rolling image updates
node-by-node give zero service downtime (the immutable-infrastructure
model — Talos Linux, Flatcar, CoreOS). Simpler machinery (no container
runtime, no second signing chain), but it assumes a single build
authority composes every node image on every service's release. It
remains the better model if the service estate turns out to be small
and single-authority; revisit trigger in reverse.

During development none of this applies: the inner loop is sstate-cached
image rebuilds (minutes) and `devtool deploy-target` (seconds, pushes a
recipe's output onto a running dev target over SSH) — dev images carry
sshd; production images do not.

Sequencing: Phase 1 fixes the package/service set → Phase 2 freezes the
partition, update and integrity architecture around it (the A/B slot map,
the application partition, dm-verity sealing and the update bundle format
are one coupled decision).

## 7. Platform/application composition (customer layers)

Any WilhelmOS integrator must be able to bundle their own kiosk
application, utilities and services **without touching WilhelmOS
recipes**. The mechanism is Yocto layering, and the layer boundary is
deliberately also the certification boundary:

- **meta-wilhelmos is a pure platform layer** — distro policy, kernel
  and hardening config, journald policy, cage/seatd and the kiosk
  session machinery. It never names a customer application.
  `wilhelm-renderer-demo` is the *reference application*: the platform's
  own GPU-stack validation vehicle and the worked example integrators
  copy — not a product component.
- **The integrator brings their own layer** (`meta-<customer>`) holding
  their application recipes, additional utilities/services (§5 patterns
  apply to those services), and their image recipe, stacked via their
  own kas config: oe-core + meta-wilhelmos + `meta-<customer>`. A
  **WilhelmOS release tag is the pinned COTS configuration baseline**
  (§12.4) — the integrator pins it exactly as WilhelmOS pins oe-core.
  Everything in the customer layer is the applicant's own ED-109A
  scope. Nested configuration control, one accountable party per level,
  consolidating at the equipment boundary (§6).

Two composition contracts make this concrete (implemented 2026-07-25):

1. **Image composition.** The kiosk image consumes the application
   through a variable — `IMAGE_INSTALL:append = " … ${KIOSK_APP}"`,
   `KIOSK_APP ?= "wilhelm-renderer-demo"` — so a customer image recipe
   just `require`s the platform kiosk image and sets `KIOSK_APP` (plus
   whatever utilities/services it adds).
2. **Session exec contract.** `cage-kiosk.service` execs a stable path,
   `/usr/libexec/kiosk-app`, which the application package must provide
   (symlink or wrapper it installs); build-time enforcement via a
   `virtual/kiosk-app` PROVIDES so exactly one package claims the role.
   This path is the seed of the platform/application interface
   description, and it makes the monolithic→split migration hedge (§6)
   invisible to the session machinery: the service execs the same path
   whether the application is baked into the rootfs or mounted from an
   app slot.

The reference application is moving to its own repo
(`kiosk-app-demo`, the integrator's worked example — standalone
binary crate, committed lockfile, release tags, zero git dependencies in
tagged releases). A staged recipe
(`recipes-graphics/kiosk-app-demo/*.bb.staged`, kept out of
bitbake's parse path until its repo exists) replaces the current
triple-git-checkout `wilhelm-renderer-demo` recipe once the renderer
crates are published and v0.1.0 is tagged; activation steps are listed
in the staged file.

Existing precedent in the same spirit: production images must override
`EXTRA_USERS_PARAMS` to replace the dev credential (§3) — platform
provides the mechanism and a dev default; the integrator owns the
production value.

### Application framework: wilhelmos_kiosk (decided 2026-07-25)

Above the platform contracts sits optional **application-layer tooling**:
[`wilhelmos_kiosk`](https://github.com/algonents/wilhelmos_kiosk), an
opinionated Rust framework crate (lifecycle trait, owned frame loop,
typed input events, ImGui guardrails, predefined chrome components —
clock, status bar; deliberately no terminal) that integrators can use
*or omit* when building their kiosk application. Key boundary decisions,
recorded in that repo's `docs/DESIGN.md`:

- **Pure library, in-process, link-time composition.** Nothing in
  WilhelmOS changes: a wilhelmos_kiosk app is an ordinary `kiosk-app`
  package satisfying the two contracts above. The platform remains
  app-framework-neutral; cage stays single-surface.
- **Certification framing**: wilhelmos_kiosk is COTS *library* evidence
  (§12.4) inside the applicant's application scope — unlike the platform
  layer boundary, it does not partition assurance. Its robustness posture
  is supervised: panic → logged → nonzero exit → the cage-kiosk unit's
  `Restart=on-failure` (§2.4.3 story unchanged).
- Runtime plugin loading ("certified shell binary + customer plugin")
  was considered and deferred; the trait surface is kept FFI-promotable
  should that model become commercially decisive.

## 8. Phase 3 — ED-109A evidence package

- PSAA template (Section 11.1) mapping WilhelmOS artifacts to objectives.
- Software Configuration Index (Section 11.16) generated from the pinned
  kas config + buildhistory + SPDX outputs.
- COTS Software Integrity Assurance Case template (Section 12.4.11).
- Mapping table: WilhelmOS evidence → Annex A objectives (Tables A-7/A-8).
- CI: QEMU boot-to-login test, image-size and manifest regression checks,
  `kernel_configcheck` gate — turning the Phase 0 verification steps into
  repeatable automated evidence.

## 9. Sequencing rationale

Hygiene and reproducibility came first because every later claim — "this
image is hardened", "this service set is minimal", "this binary matches this
source" — is only evidence if it refers to a build that can be reproduced
bit-for-bit from pinned inputs. Hardening that would be invalidated by the
kiosk stack (service stripping, partition/integrity design) waits until the
real payload exists; hardening that survives it (kernel flags, persistent
logging, credential policy) landed in Phase 0.
