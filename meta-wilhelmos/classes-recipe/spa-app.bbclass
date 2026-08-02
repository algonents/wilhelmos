# The application side of the WilhelmOS SPA composition contract
# (docs/SPA-CONTRACT.md; docs/DESIGN.md §10). Unlike the kiosk-app
# contract, an SPA provider ships a *web root* — a static single-page
# application bundle — not an executable; the browser (Cog/WPE) is
# platform machinery. A provider recipe inherits this class and points
# SPA_APP_WEBROOT at the directory its do_install populated:
#
#   inherit spa-app
#   SPA_APP_WEBROOT = "${datadir}/my-spa"
#
# The class claims the spa-app role and installs the stable web root
# /usr/share/spa-app (what the session loads as
# file:///usr/share/spa-app/index.html) as a relative symlink —
# relative so it also resolves when the rootfs is inspected from
# outside (image contract check, post-mortem mounts).

SPA_APP_WEBROOT ?= ""

# Exactly one package in an image may claim the role: the session
# machinery RDEPENDS on "spa-app"; the image picks the provider via
# its SPA_APP variable.
PROVIDES += "virtual/spa-app"
RPROVIDES:${PN} += "spa-app"

spa_app_install_link() {
    if [ -z "${SPA_APP_WEBROOT}" ]; then
        bbfatal "spa-app: the recipe must set SPA_APP_WEBROOT to the web-root directory its do_install populates (e.g. \${datadir}/my-spa)"
    fi
    if [ ! -f "${D}${SPA_APP_WEBROOT}/index.html" ]; then
        bbfatal "spa-app: SPA_APP_WEBROOT='${SPA_APP_WEBROOT}' contains no index.html — the entry point the session loads"
    fi
    install -d ${D}${datadir}
    ln -rs ${D}${SPA_APP_WEBROOT} ${D}${datadir}/spa-app
}
do_install[postfuncs] += "spa_app_install_link"

FILES:${PN} += "${datadir}/spa-app"
