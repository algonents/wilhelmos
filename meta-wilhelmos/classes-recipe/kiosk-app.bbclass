# The application side of the WilhelmOS kiosk composition contract
# (docs/KIOSK-CONTRACT.md; docs/DESIGN.md §7). A kiosk application
# recipe inherits this class and points KIOSK_APP_BINARY at the
# executable its do_install installs:
#
#   inherit kiosk-app
#   KIOSK_APP_BINARY = "${bindir}/my_app"
#
# The class claims the kiosk-app role for the package and installs the
# stable exec path cage-kiosk.service runs, /usr/libexec/kiosk-app, as
# a symlink to the application binary. The link is deliberately
# relative: it must also resolve when the rootfs is inspected from
# outside (the kiosk image's contract check, post-mortem mounts of a
# flashed stick).

KIOSK_APP_BINARY ?= ""

# Exactly one package in an image may claim the role: the session
# machinery RDEPENDS on "kiosk-app"; the image picks the provider via
# its KIOSK_APP variable.
PROVIDES += "virtual/kiosk-app"
RPROVIDES:${PN} += "kiosk-app"

# A kiosk application is by definition a Wayland GL client.
inherit features_check
REQUIRED_DISTRO_FEATURES = "wayland opengl"

kiosk_app_install_link() {
    if [ -z "${KIOSK_APP_BINARY}" ]; then
        bbfatal "kiosk-app: the recipe must set KIOSK_APP_BINARY to the application executable its do_install installs (e.g. \${bindir}/my_app)"
    fi
    if [ ! -f "${D}${KIOSK_APP_BINARY}" ] || [ ! -x "${D}${KIOSK_APP_BINARY}" ]; then
        bbfatal "kiosk-app: KIOSK_APP_BINARY='${KIOSK_APP_BINARY}' was not installed as an executable by do_install"
    fi
    install -d ${D}${libexecdir}
    ln -rs ${D}${KIOSK_APP_BINARY} ${D}${libexecdir}/kiosk-app
}
do_install[postfuncs] += "kiosk_app_install_link"
