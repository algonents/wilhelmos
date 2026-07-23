SUMMARY = "WilhelmOS kiosk validation image (cage + wilhelm_renderer imgui demo)"
DESCRIPTION = "wilhelmos-image-base plus the Wayland kiosk stack: boots into \
the cage compositor running the wilhelm_renderer imgui demo fullscreen on \
tty1, with a maintenance getty on tty2."

require wilhelmos-image-base.bb

IMAGE_INSTALL:append = " cage seatd wilhelmos-kiosk-session wilhelm-renderer-demo"

# cage-kiosk.service is WantedBy=graphical.target
SYSTEMD_DEFAULT_TARGET = "graphical.target"
