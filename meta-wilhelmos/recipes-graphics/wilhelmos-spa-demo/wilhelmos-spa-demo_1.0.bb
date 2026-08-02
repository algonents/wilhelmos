SUMMARY = "WilhelmOS reference SPA (spa-app contract worked example)"
DESCRIPTION = "Single self-contained HTML page (inline JS/CSS, canvas \
rendering, no external resources) validating the SPA profile stack end \
to end from the bundled web root, and serving as the worked packaging \
example for integrator SPA bundles (docs/SPA-CONTRACT.md)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://index.html"

S = "${UNPACKDIR}"

# spa-app claims the §10 composition-contract role (virtual/spa-app,
# stable /usr/share/spa-app web root) — see docs/SPA-CONTRACT.md.
inherit allarch spa-app

SPA_APP_WEBROOT = "${datadir}/wilhelmos-spa-demo"

do_install() {
    install -Dm0644 ${UNPACKDIR}/index.html \
        ${D}${SPA_APP_WEBROOT}/index.html
}
