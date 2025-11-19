SUMMARY = "WilhelmOS base image for x86-64 (QEMU)"
DESCRIPTION = "Minimal WilhelmOS base image derived from core-image-minimal, with extra tools."
LICENSE = "MIT"

inherit core-image

# Reuse Poky's core-image-minimal definition
require ${COREBASE}/meta/recipes-core/images/core-image-minimal.bb

# WilhelmOS-specific additions
IMAGE_INSTALL:append = " nano"

