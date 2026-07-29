SUMMARY = "WilhelmOS base image for x86-64 (QEMU + bare metal)"
DESCRIPTION = "Minimal WilhelmOS base image derived from core-image-minimal, with extra tools."
LICENSE = "MIT"

# Add a UEFI .wic image type in addition to the default formats.
# Also keep an uncompressed ext4: wrynose's default is ext4.zst, which
# runqemu only accepts in (write-discarding) snapshot mode.
IMAGE_FSTYPES += " wic ext4"
QB_DEFAULT_FSTYPE = "ext4"

# Use our custom UEFI/GPT layout when building the .wic image
WKS_FILE = "wilhelmos-efi.wks"

# Keep the ESP out of /etc/fstab: wic would hardcode the flash-time
# device (/dev/sda1), which breaks when the image is installed to a
# different disk; systemd-gpt-auto mounts the right ESP instead.
# The per-partition --no-fstab-update in the wks is parsed but ignored
# by wic 0.3.1 (update_fstab never checks part.no_fstab_update), so the
# imager-global flag is required.
WIC_CREATE_EXTRA_ARGS:append = " --no-fstab-update"

inherit core-image

# Reuse Poky's core-image-minimal definition
require ${COREBASE}/meta/recipes-core/images/core-image-minimal.bb

# WilhelmOS-specific additions. efibootmgr: UEFI boot-entry management
# from the maintenance console (install to internal disk, entry cleanup)
# without external media.
IMAGE_INSTALL:append = " util-linux sudo nano kbd efibootmgr wilhelmos-installer wh-terminus-console-font wilhelmos-vconsole-conf wilhelmos-sudoers wilhelmos-journald-conf"

# logging is done by systemd. Disable BusyBox logging
IMAGE_INSTALL:remove = "busybox-syslog"
PACKAGE_EXCLUDE:append = " busybox-syslog busybox-klogd"