SUMMARY = "Terminus 32px console font (ter-u32n)"
DESCRIPTION = "Pre-built PSF bitmap console font from the Terminus font family, \
sized for high-DPI framebuffer consoles. Selected at boot via /etc/vconsole.conf."
HOMEPAGE = "https://terminus-font.sourceforge.net/"
LICENSE = "OFL-1.1"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/OFL-1.1;md5=fac3a519e5e9eb96316656e0ca4f2b90"
PV = "4.49.1"

# unpack=0: the fetcher would otherwise gunzip the file; the console loads
# the .psf.gz directly, so ship it compressed.
SRC_URI = "file://ter-u32n.psf.gz;unpack=0"


inherit allarch

do_install() {
    install -d ${D}${datadir}/consolefonts
    install -m 0644 ${UNPACKDIR}/ter-u32n.psf.gz \
        ${D}${datadir}/consolefonts/ter-u32n.psf.gz
}

FILES:${PN} = "${datadir}/consolefonts"
