SUMMARY = "WilhelmOS kiosk validation image (cage + wilhelm_renderer imgui demo)"
DESCRIPTION = "wilhelmos-image-base plus the Wayland kiosk stack: boots into \
the cage compositor running the wilhelm_renderer imgui demo fullscreen on \
tty1, with a maintenance getty on tty2."

require wilhelmos-image-base.bb

IMAGE_INSTALL:append = " cage seatd wilhelmos-kiosk-session wilhelm-renderer-demo"

# Bare metal needs the modular GPU drivers plus their firmware; the QEMU
# image stays lean (virtio-gpu is built in).
IMAGE_INSTALL:append:genericx86-64 = " kernel-modules linux-firmware-i915 linux-firmware-xe"

# cage-kiosk.service is WantedBy=graphical.target
SYSTEMD_DEFAULT_TARGET = "graphical.target"
