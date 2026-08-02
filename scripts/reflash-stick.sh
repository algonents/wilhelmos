#!/bin/sh
# reflash-stick.sh — flash the built hw kiosk image to a USB install
# stick and recreate the wicstore partition (image + sha256) consumed
# by wilhelmos-install.
#
#   sudo sh scripts/reflash-stick.sh /dev/sdX
#
# Build state is found via KAS_WORK_DIR (same default as the Makefile:
# ../wilhelmos-build next to this checkout).
#
# Flash discipline (see docs/DESIGN.md §4): suspend targets must be
# masked on the host, no partition of the stick may be mounted, direct
# I/O so progress reflects real device writes, and the flashed rootfs
# is verified afterwards (biggest-file checksum) before the stick is
# trusted for a boot test.
set -eu

REPO=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
KAS_WORK_DIR=${KAS_WORK_DIR:-$REPO/../wilhelmos-build}
MACHINE=genericx86-64
# Override for other profiles (DESIGN.md §10), e.g. IMAGE=wilhelmos-image-spa
IMAGE=${IMAGE:-wilhelmos-image-kiosk}

DEPLOY=$KAS_WORK_DIR/build/tmp/deploy/images/$MACHINE
WIC=$(readlink -f "$DEPLOY/$IMAGE-$MACHINE.rootfs.wic")
ROOTFS=$KAS_WORK_DIR/build/tmp/work/$(echo "$MACHINE" | tr - _)-poky-linux/$IMAGE/1.0/rootfs

[ $# -eq 1 ] || { echo "usage: sudo sh $0 /dev/sdX" >&2; exit 1; }
STICK=$1
[ "$(id -u)" -eq 0 ] || { echo "error: run as root: sudo sh $0 $STICK" >&2; exit 1; }
# A pasted-but-nonexistent device name would become a regular file in
# /dev (devtmpfs = RAM) and the flash would "succeed" instantly.
[ -b "$STICK" ] || { echo "error: $STICK is not a block device" >&2; exit 1; }
case "$STICK" in /dev/nvme*) echo "error: refusing an NVMe device" >&2; exit 1 ;; esac
[ -f "$WIC" ] || { echo "error: no built image at $DEPLOY" >&2; exit 1; }

echo "Image:  $WIC"
echo "Target:"
lsblk -o NAME,SIZE,MODEL,TRAN "$STICK"
printf 'This ERASES the stick. Type YES to flash: '
read -r ANSWER
[ "$ANSWER" = "YES" ] || { echo "aborted (nothing written)"; exit 1; }

# Desktops automount; a mounted partition during the write wedges it.
for P in $(lsblk -nro NAME,MOUNTPOINT "$STICK" | awk '$2 {print $1}'); do
    umount "/dev/$P"
done

echo "Flashing (direct I/O, progress = real device writes)..."
dd if="$WIC" of="$STICK" bs=4M oflag=direct conv=fsync status=progress
sync
blockdev --rereadpt "$STICK"
udevadm settle

echo "Recreating wicstore partition..."
# The dd'd image carries an image-sized GPT: put the backup header back
# at the end of the stick, then append a partition over the free space.
sfdisk -q --relocate gpt-bak-std "$STICK"
echo ',,L' | sfdisk -q -a "$STICK"
blockdev --rereadpt "$STICK"
udevadm settle
P3=${STICK}3
[ -b "$P3" ] || { echo "error: $P3 did not appear" >&2; exit 1; }
mkfs.ext4 -q -F -L wicstore "$P3"

MNT=$(mktemp -d)
trap 'umount "$MNT" 2>/dev/null || true; rmdir "$MNT" 2>/dev/null || true' EXIT
mount "$P3" "$MNT"
echo "Copying image into wicstore (~3 GB)..."
cp "$WIC" "$MNT/wilhelmos.wic"
(cd "$MNT" && sha256sum wilhelmos.wic > wilhelmos.wic.sha256)
umount "$MNT"

echo "Verifying flashed rootfs (biggest-file checksum)..."
mount -o ro "${STICK}2" "$MNT"
A=$(sha256sum "$MNT"/usr/lib/libLLVM* | awk '{print $1}')
B=$(sha256sum "$ROOTFS"/usr/lib/libLLVM* | awk '{print $1}')
umount "$MNT"
[ "$A" = "$B" ] || { echo "FAIL: flashed rootfs differs from build rootfs" >&2; exit 1; }
echo "Flash verified OK."

echo "Done — safe to remove after: udisksctl power-off -b $STICK"
