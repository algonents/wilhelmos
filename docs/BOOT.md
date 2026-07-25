# WilhelmOS Boot Walkthrough — power button to kiosk app

This document walks through everything that happens between pressing the
power button and the demo kiosk app rendering its first frame, on the
bare-metal kiosk image (`wilhelmos-image-kiosk`, `MACHINE=genericx86-64`,
booted from USB). It is written to be read start-to-finish; each stage
explains the concepts it introduces. Everything here describes *this*
repository's actual configuration — file paths in the tree are cited so you
can cross-check.

The whole chain, compressed to one line:

```
UEFI firmware → systemd-boot → kernel (EFI stub) → systemd (PID 1)
      → seatd + udev/i915 → cage (Wayland/DRM) → /usr/libexec/kiosk-app (GL)
```

And the same thing as a picture, with the artifacts each stage reads:

```
┌────────────┐  reads GPT, finds ESP     ┌──────────────────┐
│ UEFI       │──────────────────────────▶│ systemd-boot     │  (on the ESP,
│ firmware   │  leaves a GOP framebuffer │ (boot menu, 3 s) │   partition 1)
└────────────┘                           └────────┬─────────┘
                                                  │ loads bzImage + cmdline
                                                  ▼
                                         ┌──────────────────┐
                                         │ Linux kernel      │ no initramfs;
                                         │ (EFI stub entry)  │ mounts ext4 root
                                         └────────┬─────────┘ by PARTLABEL
                                                  │ execs /sbin/init
                                                  ▼
                                         ┌──────────────────┐
                                         │ systemd (PID 1)   │ journald, udev,
                                         │ → graphical.target│ seatd, getty@tty2
                                         └────────┬─────────┘
                            udev loads i915 ──────┤
                            (/dev/dri/renderD128) │ cage-kiosk.service (tty1)
                                                  ▼
                                         ┌──────────────────┐
                                         │ cage (wlroots)    │ DRM master, KMS
                                         │ Wayland kiosk     │ modeset, EGL
                                         └────────┬─────────┘
                                                  │ spawns, via Wayland socket
                                                  ▼
                                         ┌──────────────────┐
                                         │ kiosk-app         │ GLFW-Wayland,
                                         │ (demo, fullscreen)│ GLES on iris
                                         └──────────────────┘
```

---

## Stage 0 — Firmware (UEFI)

When power arrives, the CPU starts executing the platform firmware (what
the industry still colloquially calls "the BIOS", though on every machine
we target it is **UEFI** — Unified Extensible Firmware Interface). The
firmware does three things we care about:

1. **POST / hardware init** — trains memory, enumerates PCIe, initializes
   the iGPU enough to put a picture on screen.

2. **Sets up a firmware framebuffer via GOP.** The **Graphics Output
   Protocol** is UEFI's standard way of giving pre-OS software a dumb
   linear framebuffer: "here is a block of memory; pixels you write appear
   on screen." No acceleration, no mode switching after boot — but it is
   how the boot menu and early kernel messages are visible before any real
   GPU driver exists. Remember this framebuffer; it becomes important (and
   nearly ruinous) in Stage 3.

3. **Chooses what to boot.** UEFI understands GPT partition tables and FAT
   filesystems natively. It scans attached disks for an **EFI System
   Partition (ESP)** — a FAT partition with a well-known GUID type — and
   runs an EFI executable from it. For removable media like our USB stick,
   it uses the fallback path `\EFI\BOOT\BOOTX64.EFI`.

On our image, the disk layout comes from
`meta-wilhelmos/files/wic/wilhelmos-efi.wks`:

```
part /boot --source bootimg-efi --sourceparams="loader=systemd-boot" --label boot --active
part /     --source rootfs --fstype=ext4 --label rootfs
bootloader --ptable=gpt --timeout=3 --append="root=PARTLABEL=rootfs rootwait"
```

Two GPT partitions: partition 1 is the ESP (FAT, label `boot`) holding
systemd-boot and the kernel; partition 2 is the ext4 root filesystem
(GPT partition label `rootfs` — that label is how the kernel will find it
later). The `.wic` file you flash with `bmaptool`/`dd` is a byte-exact
image of this whole disk, GPT headers included.

One constraint for now: **Secure Boot must be disabled** in firmware setup.
Nothing in the chain is signed yet (that is Phase 2 design work, DESIGN.md
§6); with Secure Boot on, the firmware would refuse to run our unsigned
`BOOTX64.EFI`.

## Stage 1 — systemd-boot

The `BOOTX64.EFI` that the firmware launches is **systemd-boot** (selected
by `EFI_PROVIDER = "systemd-boot"` in
`meta-wilhelmos/conf/distro/wilhelmos.conf`). Despite the name, it has
nothing to do with the systemd init daemon — it is a deliberately tiny UEFI
boot *menu*, a few tens of kilobytes. It does not understand ext4, does not
contain drivers, and cannot load anything from the root partition; it only
reads the FAT ESP it lives on.

Compare with GRUB, the other common choice: GRUB is a small operating
system in its own right (filesystem drivers, scripting language, modules).
systemd-boot's near-absence of functionality is exactly why we picked it —
same "restriction of functionality" argument (ED-109A §12.4.11) as choosing
cage over Weston later in the chain.

At boot it reads `loader/loader.conf` and one **entry file** per bootable
configuration under `loader/entries/` (WIC's `bootimg-efi` plugin generated
these at build time from the `.wks` file). Our single entry names the
kernel image (`bzImage`, also on the ESP) and the **kernel command line**:

```
root=PARTLABEL=rootfs rootwait
```

The `--timeout=3` in the `.wks` gives you a 3-second window to interrupt
the menu (hold a key during boot); otherwise the default entry boots. The
debug image variant (`wilhelmos-efi-debug.wks`, built via `kas/debug.yaml`)
differs *only* in this command line — it appends `console=`,
`systemd.show_status=1`, `loglevel=7` etc. so the whole boot narrates
itself on screen and serial.

How does the kernel — an ELF-shaped Linux image — get executed by UEFI
firmware that only runs EFI binaries? Because the kernel is *also* an EFI
binary: `CONFIG_EFI_STUB=y` (`meta-wilhelmos/recipes-kernel/linux/files/usb-root.cfg`)
prepends a small PE/COFF header and EFI entry point to the kernel, so
systemd-boot can hand it to the firmware's normal "load and run this EFI
program" service. The stub then fetches the memory map from the firmware,
calls `ExitBootServices()` (the point of no return — firmware boot
services cease to exist), and jumps into normal kernel startup.

## Stage 2 — Kernel

The kernel now decompresses itself and initializes: CPUs, memory
management, PCI enumeration, then drivers. Three kernel-side facts shape
everything downstream. All the cited `CONFIG_` options live in our config
fragments under `meta-wilhelmos/recipes-kernel/linux/files/` (applied by
`linux-yocto_%.bbappend`).

### 2a. There is no initramfs

Many distros boot through an **initramfs** — a small compressed userspace
the bootloader loads alongside the kernel, whose job is to load whatever
modules are needed to find the real root filesystem, then pivot into it.
WilhelmOS doesn't have one. Instead, everything needed to reach the root
filesystem is **built into the kernel image** (`=y`, not `=m`), in
`usb-root.cfg`:

- USB host controller + mass storage: `CONFIG_USB_XHCI_HCD`,
  `CONFIG_USB_STORAGE`, `CONFIG_BLK_DEV_SD` — the USB stick appears as
  `/dev/sda`.
- `CONFIG_EFI_PARTITION` — parse GPT tables (this is also what makes
  `PARTLABEL=` resolvable).
- `CONFIG_EXT4_FS` — mount the root partition.
- `CONFIG_DEVTMPFS` + `_MOUNT` — the kernel itself populates and mounts
  `/dev` before any userspace runs.

Fewer moving parts, one less build artifact to certify, and one less place
for boot to fail — at the cost of a slightly larger kernel. For a
fixed-function appliance this is the right trade.

### 2b. `root=PARTLABEL=rootfs rootwait`

USB devices enumerate *asynchronously* — the stick may take several seconds
to probe, long after the kernel would otherwise try to mount root.
`rootwait` tells the kernel to wait indefinitely for the root device to
appear rather than panicking. `root=PARTLABEL=rootfs` identifies the
partition by its GPT partition label instead of a device name like
`/dev/sda2` — device names depend on probe order and would break the moment
a second disk is present. (Note the distinction: GPT *partition* label, set
in the `.wks`, not the ext4 *filesystem* label, which happens to be
`rootfs` too.)

Once `/dev/sda2` shows up, the kernel mounts it read-write as `/` and
executes `/sbin/init` — which on WilhelmOS is a symlink into systemd
(`INIT_MANAGER = "systemd"`; there is no sysvinit anywhere in the image).
The kernel's job as "the thing in charge of boot" ends here; from now on it
only serves syscalls.

### 2c. SimpleDRM — the graphics stopgap

Before leaving the kernel, one more driver deserves its own heading because
it caused our first bare-metal boot failure.

Remember the GOP framebuffer from Stage 0? The firmware's dumb pixel
buffer survives `ExitBootServices()`. The kernel picks it up via
`CONFIG_SYSFB_SIMPLEFB` and wraps it in **SimpleDRM**
(`CONFIG_DRM_SIMPLEDRM=y`, in `gpu.cfg`) — a minimal DRM driver that
exposes the firmware framebuffer as a real-looking DRM device,
`/dev/dri/card0`, within the first second of boot.

**DRM** (Direct Rendering Manager) is the kernel's GPU subsystem; **KMS**
(Kernel Mode Setting) is its display-control half — choosing resolutions,
connecting framebuffers to outputs ("connectors"), committing new frames
for scanout. A DRM device typically exposes two device nodes:

- `/dev/dri/cardN` — the **primary node**: modesetting, connectors, page
  flips. Owning this ("being DRM master") is what lets a compositor drive
  the display.
- `/dev/dri/renderDN` (N ≥ 128) — the **render node**: GPU acceleration
  *without* display control. This is what EGL/Mesa opens to get hardware
  OpenGL.

SimpleDRM, being just a wrapped firmware framebuffer, has **no render
node** — there is no GPU behind it, only memory-mapped pixels. Any GL on
top of it is software rendering (llvmpipe/pixman). It exists so that
*something* can display early, and so the system still boots to a picture
on GPUs we have no driver for.

The real driver for our Arrow Lake iGPU is **i915**, and it is a *module*
(`CONFIG_DRM_I915=m`), loaded from the root filesystem by udev in Stage 3 —
seconds to minutes after SimpleDRM is already up. That gap between
"`card0` exists" and "the real GPU is ready" is the race at the heart of
the first-boot post-mortem (see Stage 5 and DESIGN.md §4).

Why is i915 a module and not built in? Because `genericx86-64` must boot on
Intel *and* AMD hardware: `gpu.cfg` carries i915, xe, and amdgpu as
modules, plus their firmware packages in the image, and udev loads only the
one matching the actual PCI device. Building all of them into the kernel
would bloat every boot with every vendor's driver.

## Stage 3 — systemd brings up userspace

systemd, now PID 1, reads its configuration and computes a dependency graph
of **units** (services, sockets, mounts, targets) needed to reach the
**default target**. For the kiosk image that is `graphical.target`
(`SYSTEMD_DEFAULT_TARGET = "graphical.target"` in
`wilhelmos-image-kiosk.bb`). A "target" is just a named synchronization
point — a bundle of units, roughly what runlevels were in sysvinit.

Everything below happens *concurrently*, ordered only by declared
dependencies — that's systemd's model, and why a service that needs
hardware must say so explicitly rather than assume "it booted before me".
The pieces we care about:

- **systemd-journald** starts first among them (almost everything depends
  on logging). On WilhelmOS the journal is **persistent** — stored in
  `/var/log/journal` on the ext4 root, not in RAM — via
  `Storage=persistent` (`recipes-core/systemd/files/10-wilhelmos-persistent.conf`)
  plus a distro-level fix keeping `/var/log` on real storage. This is a
  deliberate diagnosability feature: after a failed boot you can pull the
  stick, mount it on the build host, and read that boot's complete journal
  offline (`journalctl -D <mount>/var/log/journal`). No serial cable
  needed. This is exactly how the first-boot failure was diagnosed.

- **systemd-udevd** starts and performs **coldplug**: it replays kernel
  uevents for every device found during boot, matches them against
  modalias tables, and loads matching modules from `/lib/modules`. This is
  what loads **i915** for PCI device `8086:7d67` (the Core Ultra 7 265K
  iGPU). i915 in turn loads GuC/HuC/DMC firmware blobs from
  `/lib/firmware/i915` (packaged via `linux-firmware-i915` in the kiosk
  image), initializes the GPU, registers `/dev/dri/card1` — and crucially
  creates `/dev/dri/renderD128`, the signal that hardware GL now exists.
  On a slow USB stick this can take a while: the first boot measured
  ~150 s from kernel start to i915 bind (fast media brings it way down).

- **systemd-vconsole-setup** applies `vconsole.conf` (fr_CH keymap,
  Terminus font) to the virtual consoles.

- **seatd.service** — see Stage 4.

- **getty@tty2.service** — a login prompt on virtual terminal 2, the
  maintenance console. The kiosk owns tty1; Ctrl-Alt-F2 switches VTs to
  reach a shell (dev user `wilhelmos`, see CLAUDE.md). Note there is
  deliberately *no* getty on tty1 — `cage-kiosk.service` declares
  `Conflicts=getty@tty1.service` so the two can never fight over the same
  terminal.

- Known noise: `serial-getty@ttyS0` restart-loops on this workstation
  because oe-core's x86 defaults assume a serial port the board doesn't
  usably have. Harmless; silencing it in production images is on the TODO
  backlog.

### What are VTs/ttys here, anyway?

The kernel provides ~63 **virtual terminals** (`/dev/tty1`…): text
consoles multiplexed onto the display, switched with Ctrl-Alt-Fn. A
Wayland compositor doesn't render *into* a VT — it takes over the display
entirely via KMS — but it still *occupies* one, so that VT switching works
as the escape hatch between kiosk (tty1) and maintenance shell (tty2).

## Stage 4 — Seat management: seatd

Opening DRM and input devices (`/dev/dri/card*`, `/dev/input/event*`) is
privileged — you don't want any process to be able to grab the keyboard.
The conventional broker for this is systemd-**logind**, which ties device
access to PAM login sessions. WilhelmOS runs no PAM and has no login
session for the kiosk, so we use **seatd** instead: a deliberately tiny
daemon (single-digit-thousands of lines — same minimalism argument again)
that listens on `/run/seatd.sock` and hands opened device file descriptors
to whoever legitimately holds the "seat".

The kiosk session runs as a dedicated unprivileged system user, `kiosk`
(created by `recipes-graphics/cage/wilhelmos-kiosk-session_1.0.bb`), whose
supplementary groups — `video`, `input`, `render`, `seat` — grant exactly
the device access the compositor needs and nothing more. cage is told to
use seatd via `LIBSEAT_BACKEND=seatd` (libseat is the client library;
wlroots uses it for all device opening).

## Stage 5 — cage-kiosk.service: the launch gate

Now the centerpiece:
`meta-wilhelmos/recipes-graphics/cage/files/cage-kiosk.service`, the unit
that starts the compositor. It is worth reading line by line — nearly every
line encodes a lesson learned. The highlights:

```ini
[Unit]
Requires=seatd.service
After=seatd.service systemd-user-sessions.service getty@tty1.service
Conflicts=getty@tty1.service
```

Run after seatd, and evict any getty from tty1.

```ini
[Service]
User=kiosk
TTYPath=/dev/tty1
```

The compositor is *not* root. It runs as the `kiosk` user, attached to
tty1, with device access brokered by seatd (Stage 4).

```ini
ExecStartPre=/bin/sh -c '... while [ ! -S /run/seatd.sock ] ...'   # ≤5 s
```

Gate #1. `After=seatd.service` only orders *service start-up*, and seatd is
`Type=simple` — systemd considers it "started" the moment it forks, possibly
before its socket exists. So we poll for the actual socket. General
systemd lesson: **`After=` orders events; it does not guarantee
readiness.** Gate on the resource you actually need.

```ini
ExecStartPre=/bin/sh -c '... while ! ls /dev/dri/renderD* ...'      # ≤60 s
```

Gate #2, the fix from the first bare-metal boot. Recall the race from
Stage 2c: SimpleDRM's `card0` exists within a second, but i915 — modular,
loading firmware from a slow USB stick — bound ~150 s in. On the failed
boot, cage started 12 s *before* i915: wlroots enumerated the only GPU
present (SimpleDRM), found no hardware EGL, fell back to software
compositing, and the demo client couldn't create a GL context at all (no
render node). When i915 finally hotplugged, the already-built session
couldn't adopt it — every atomic commit on the new output failed `busy`,
and the session was wedged. Cursor on black screen until reboot.

The fix gates on `/dev/dri/renderD*` because **only native GPU drivers
create render nodes** — it is a precise "real GPU is ready" signal that
SimpleDRM can never spuriously satisfy. After 60 s it proceeds anyway, so
hardware we have no driver for still boots — into an *intentional*
software-rendered session this time (`WLR_RENDERER_ALLOW_SOFTWARE=1` lets
wlroots accept llvmpipe instead of refusing; the variable is inert when
real hardware GL exists).

```ini
ExecStart=/usr/bin/cage -ds -- /usr/libexec/kiosk-app
Restart=on-failure
```

Finally cage itself: `-d` disables client decorations, `-s` allows VT
switching (our maintenance escape hatch). If the compositor (or the app,
whose death takes cage down with it) exits nonzero, systemd restarts the
whole session after 2 s — the platform's first, crude layer of ED-109A
§2.4.3 fault recovery.

Note what the ExecStart does *not* say: any application name.
`/usr/libexec/kiosk-app` is a stable path contract (DESIGN.md §7) —
whichever application package the image installs provides it. On this
image, `KIOSK_APP ?= "wilhelmos-kiosk-demo"` (the reference app from
`algonents/wilhelmos-kiosk-demo`); a customer image overrides `KIOSK_APP`
with their own package (eventually, sky_guard_client) and nothing in the
session machinery changes.

## Stage 6 — cage: the compositor takes the display

**cage** is a Wayland kiosk compositor built on **wlroots** (the compositor
toolkit also underlying Sway). "Kiosk" is architectural: cage can only run
one application fullscreen — there is no window management to misconfigure
(the reason it was chosen over Weston; DESIGN.md §4, "Compositor
decision").

Why a compositor at all, for one fullscreen app? Because the app is built
on GLFW, and **GLFW has no direct DRM/KMS backend** — it can only create
windows on X11 or Wayland. Without a compositor we'd be rewriting the
renderer's windowing on raw EGL/GBM. cage is the thinnest possible adapter
between "GLFW wants a Wayland server" and "the hardware speaks DRM/KMS".

On startup cage, via wlroots:

1. Asks seatd to open the DRM device and becomes **DRM master** on the
   real GPU (`card1`, i915) — the exclusive right to modeset and flip.
2. Performs a KMS **modeset**: picks the connected output, its native
   mode, and takes over scanout. (This is the moment the firmware
   framebuffer's last picture vanishes.)
3. Initializes its own renderer: EGL on the render node via **GBM** (the
   Generic Buffer Manager, Mesa's allocator for scanout-capable buffers).
   On our hardware that means Mesa's **iris** driver — the journal line to
   look for is wlroots reporting the renderer, e.g. `Intel(R) Graphics
   (ARL)`.
4. Opens input devices (again via seatd/libinput).
5. Creates the **Wayland socket** (`$XDG_RUNTIME_DIR/wayland-0`, i.e.
   `/run/cage/wayland-0`) and only *then* spawns the child process,
   `/usr/libexec/kiosk-app`, with `WAYLAND_DISPLAY` set.

## Stage 7 — The kiosk app renders

The demo app (Rust, wilhelm_renderer + wilhelm_renderer_imgui, windowing
via GLFW's Wayland backend) starts, connects to the Wayland socket, and:

1. Creates a fullscreen Wayland surface (via GLFW /
   `Window::new_fullscreen`; cage would force fullscreen regardless — that
   is its one job).
2. Creates its own EGL context — again Mesa iris on `renderD128`,
   hardware-accelerated GLES 3.2. Note the GLX-less detail: WilhelmOS opts
   out of X11 entirely, so there is no `libGL.so` with GLX; the app loads
   GL at runtime through GLVND's vendor-neutral libEGL/libOpenGL
   (`DISTRO_FEATURES:append = " glvnd"`, and the renderer's
   `GLRENDERER_LINK_GL` switch exists precisely for this).
3. Renders each frame into a GPU buffer and commits it to its Wayland
   surface.

From there the steady-state frame loop is a relay:

```
app: draw with GLES → eglSwapBuffers → Wayland commit
cage: receive buffer → (fullscreen case: ideally scan it out directly)
      → KMS atomic commit on the i915 output
display: next vblank shows the frame
```

At this point `systemctl status cage-kiosk` is `active (running)`, the
demo is on screen with hardware GL, and Ctrl-Alt-F2 drops you to the
maintenance getty. That is the validated state as of 2026-07-25 — the
demo app, note, not sky_guard_client, which does not exist as a recipe
yet.

---

## Watching it happen: the boot, stage by stage, in the journal

The journal is persistent, so you can do this on the machine or offline on
a mounted stick (`journalctl -D <mount>/var/log/journal`). Useful probes,
mapped to the stages above:

| Stage | Command | What to look for |
|---|---|---|
| 2 kernel/root | `journalctl -b -k \| grep -iE 'command line\|sda\|ext4'` | cmdline with `root=PARTLABEL`, USB probe timing, root mount |
| 2c SimpleDRM | `journalctl -b -k \| grep -i simple` | `simpledrm` registering `card0` in the first second |
| 3 i915 bind | `journalctl -b -k \| grep -i i915` | firmware loads, `[drm] Initialized i915`; note the timestamp gap vs SimpleDRM |
| 4 seatd | `journalctl -b -u seatd` | socket up, client (cage) attached |
| 5–6 cage | `journalctl -b -u cage-kiosk` | the two ExecStartPre waits, wlroots renderer line (`iris`/`ARL`), modeset |
| overall order | `journalctl -b -o short-monotonic -u cage-kiosk -u seatd -k \| grep -iE 'i915\|renderD\|cage'` | one merged timeline of the race the service gates against |
| blame | `systemd-analyze` / `systemd-analyze critical-chain graphical.target` | where boot time actually goes |

A good exercise: find, in a real boot's journal, the monotonic timestamps
of (a) SimpleDRM registering, (b) i915 creating the render node, and
(c) cage's ExecStart — and confirm the gate held (c) until after (b).

## Further reading in this repo

- DESIGN.md §4 — the full first-bare-metal-boot post-mortem this document
  keeps referring to, plus the cage and iGPU decisions.
- DESIGN.md §7 — the `KIOSK_APP` / `kiosk-app` composition contract.
- `meta-wilhelmos/recipes-graphics/cage/files/cage-kiosk.service` — the
  annotated unit itself.
- `meta-wilhelmos/recipes-kernel/linux/files/*.cfg` — every kernel option
  named here, with comments.
