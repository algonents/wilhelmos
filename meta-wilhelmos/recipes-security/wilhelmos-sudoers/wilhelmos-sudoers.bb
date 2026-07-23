SUMMARY = "WilhelmOS sudoers policy"
DESCRIPTION = "Installs /etc/sudoers.d/10-wheel granting members of the wheel \
group full sudo access (password required)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://10-wheel"

do_install() {
    install -d ${D}${sysconfdir}/sudoers.d
    install -m 0440 ${WORKDIR}/10-wheel ${D}${sysconfdir}/sudoers.d/10-wheel
}

FILES:${PN} = "${sysconfdir}/sudoers.d"

RDEPENDS:${PN} += "sudo"
