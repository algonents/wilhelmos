SUMMARY = "WilhelmOS base image for x86-64 (QEMU + bare metal)"
DESCRIPTION = "Minimal WilhelmOS base image derived from core-image-minimal, with extra tools."
LICENSE = "MIT"

# Add a UEFI .wic image type in addition to the default formats (ext4, etc.)
# This keeps your existing QEMU workflow intact.
IMAGE_FSTYPES += " wic"

# Use our custom UEFI/GPT layout when building the .wic image
WKS_FILE = "wilhelmos-efi.wks"

inherit core-image

# Reuse Poky's core-image-minimal definition
require ${COREBASE}/meta/recipes-core/images/core-image-minimal.bb

# WilhelmOS-specific additions
IMAGE_INSTALL:append = " nano kbd kbd-consolefonts fonts-setup"