SUMMARY = "Cage, a Wayland kiosk compositor"
DESCRIPTION = "Cage displays a single fullscreen application and nothing \
else — architecturally incapable of more, which is exactly the property \
WilhelmOS wants from its kiosk compositor (see docs/DESIGN.md section 4)."
HOMEPAGE = "https://github.com/cage-kiosk/cage"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=e3d06ce025701c9a0b391f15902ce8ed"

SRC_URI = " \
    git://github.com/cage-kiosk/cage.git;protocol=https;nobranch=1 \
    file://0001-xdg-shell-guard-pre-commit-fullscreen-requests.patch \
"
SRCREV = "f9626f79519f8ee22d7bb0c3880a66791d82f923"

DEPENDS = " \
    wayland-native \
    wlroots \
    wayland \
    wayland-protocols \
    libxkbcommon \
"

inherit meson pkgconfig features_check

REQUIRED_DISTRO_FEATURES = "wayland opengl"

EXTRA_OEMESON = "-Dman-pages=disabled"

# xkbcommon loads keymap data from xkeyboard-config at runtime
RDEPENDS:${PN} += "xkeyboard-config"
