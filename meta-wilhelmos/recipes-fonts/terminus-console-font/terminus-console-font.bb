SUMMARY = "Terminus 32px console font (ter-u32n)"
LICENSE = "CLOSED"

inherit allarch

# We are not using SRC_URI/fetch; we install directly from the layer's files dir.
do_install() {
    install -d ${D}${datadir}/consolefonts
    install -m 0644 ${THISDIR}/files/ter-u32n.psf.gz \
        ${D}${datadir}/consolefonts/ter-u32n.psf.gz
}

FILES:${PN} = "${datadir}/consolefonts"
