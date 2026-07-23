SUMMARY = "WilhelmOS vconsole configuration (console font/keymap)"
DESCRIPTION = "Installs /etc/vconsole.conf selecting the Terminus ter-u32n \
console font and the fr_CH keymap."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://vconsole.conf"

do_install() {
    install -d ${D}${sysconfdir}
    install -m 0644 ${WORKDIR}/vconsole.conf ${D}${sysconfdir}/vconsole.conf
}

FILES:${PN} = "${sysconfdir}/vconsole.conf"

RDEPENDS:${PN} += "systemd wh-terminus-console-font"
