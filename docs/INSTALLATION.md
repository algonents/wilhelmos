# WilhelmOS Installation

How a WilhelmOS image gets onto target hardware: the current USB-based
procedure (validated on real equipment), and the roadmap from one box to a
fleet. Update mechanics beyond installation are owned by the Phase 2
partition/update design (DESIGN.md §6); this document stays on the
"getting the OS onto a disk" side and only sketches where the two meet.

## 1. The install medium

One USB stick carries the image twice, in two forms:

| Partition | Contents | Role |
|-----------|----------|------|
| p1 (`boot`) | ESP, systemd-boot | boots the maintenance system |
| p2 (`rootfs`) | the image, unpacked | live WilhelmOS incl. `wilhelmos-install` |
| p3 (`wicstore`) | `wilhelmos.wic` + `.sha256` | pristine payload to be installed |

The stick boots a full WilhelmOS (same image as the target will run), and the
installer writes the *payload copy* to the internal disk — it never tries to
re-assemble a disk image from its own live partitions. Same pattern as any
OS install USB: live system + installation payload.

## 2. Building and flashing the stick

```sh
make build KAS_FILE="kas/qemu-wrynose.yaml:kas/hw.yaml"   # hw kiosk image
sudo sh scripts/reflash-stick.sh /dev/sdX                 # flash + wicstore + verify
```

`reflash-stick.sh` encodes the whole procedure: unmounts automounted
partitions, flashes with direct I/O, relocates the backup GPT header,
recreates the `wicstore` partition (image + sha256), and verifies the flashed
rootfs against the build tree before the stick is trusted for a boot test.
Host-side flash discipline (suspend masking, corruption history, why
verification is non-negotiable) is documented in DESIGN.md §4.

## 3. Installing to a target disk

On the target machine:

1. Boot the stick via the firmware boot menu (after a previous install the
   firmware prefers the internal "WilhelmOS" entry — a one-shot USB boot must
   be selected explicitly).
2. Log in on the console (tty2) and sanity-check that `/` is on the stick
   (`lsblk`); the installer's live-root guard also enforces this.
3. Run:

   ```sh
   sudo wilhelmos-install /dev/nvme0n1
   ```

The installer then, unattended:

- refuses to write to the install medium itself or the disk backing the
  running root
- sha256-verifies the payload image, then writes it to the target
- **grows the root partition to fill the disk** (relocate backup GPT, extend
  p2, `resize2fs`) — interim behavior until the Phase 2 A/B layout assigns
  the spare capacity to a writable data partition instead
- relabels the target rootfs (`rootfs` → `wilhelmos-root`) and patches the
  loader entries to match, so a later stick boot can never bind to the
  internal disk by accident
- retires stale firmware boot entries (vendor OS, previous WilhelmOS) and
  registers "WilhelmOS" first in BootOrder

Power off, remove the stick, boot. Verify with `df -h /` (rootfs spans the
disk) and `lsblk`. The journal is persistent — a failed install/boot can be
post-mortemed from the stick or target disk offline.

## 4. Scaling: from one box to fifty positions

Installation and update are different problems with different tools:

**First install** (bare metal → running baseline):

1. *USB sticks* — the current mechanism; honest up to roughly a dozen boxes.
2. *Depot staging* — image the boxes at a bench before racking (how ATM
   equipment commonly ships): on-site work reduces to mount, cable, boot,
   site acceptance test.
3. *PXE provisioning* — the fleet answer: UEFI network-boots the same
   maintenance system from a deployment server (TFTP/HTTP), and the
   installer fetches the image over HTTP instead of from `wicstore` —
   everything else (write, grow, relabel, boot entry) is unchanged. Requires
   signature verification on the payload, not just a checksum.

**Updates** (50 installed positions → new baseline) are *not* reinstalls.
Per DESIGN.md §6: atomic A/B image updates (candidate tooling RAUC) driven
by a deployment server (candidate: Eclipse hawkBit) that tracks which
position runs which baseline — that inventory doubles as ED-109A §7
configuration-management evidence. Operationally: canary positions first,
then rolling waves sized so the ops room never loses more than N positions
at once; the new image is staged onto the inactive slot with zero downtime
and the cutover is one reboot per position inside an approved maintenance
window, with automatic rollback on a failed boot.
