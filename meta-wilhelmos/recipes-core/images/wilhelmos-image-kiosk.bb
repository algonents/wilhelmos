SUMMARY = "WilhelmOS kiosk validation image (cage + wilhelm_renderer imgui demo)"
DESCRIPTION = "wilhelmos-image-base plus the Wayland kiosk stack: boots into \
the cage compositor running the wilhelm_renderer imgui demo fullscreen on \
tty1, with a maintenance getty on tty2."

require wilhelmos-image-base.bb

# The kiosk application (§7 composition contract): customer images
# require this image and override KIOSK_APP with their own application
# package, which must provide /usr/libexec/kiosk-app (virtual/kiosk-app).
# Default: the WilhelmOS reference application.
KIOSK_APP ?= "kiosk-app-demo"

IMAGE_INSTALL:append = " cage seatd wilhelmos-kiosk-session ${KIOSK_APP}"

# Bare metal needs the modular GPU drivers plus their firmware; the QEMU
# image stays lean (virtio-gpu is built in). Firmware is deliberately
# vendor-agnostic — genericx86-64 means Intel (i915/xe) and AMD (amdgpu)
# iGPUs boot the same image; rtl-nic covers the Realtek 2.5GbE PHYs common
# on mini-PC test/deployment hardware (see docs/HARDWARE.md).
IMAGE_INSTALL:append:genericx86-64 = " kernel-modules \
    linux-firmware-i915 linux-firmware-xe \
    linux-firmware-amdgpu linux-firmware-rtl-nic"

# cage-kiosk.service is WantedBy=graphical.target
SYSTEMD_DEFAULT_TARGET = "graphical.target"
