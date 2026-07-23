SUMMARY = "WilhelmOS sudoers policy"
DESCRIPTION = "Installs /etc/sudoers.d/10-wheel granting members of the wheel \
group full sudo access (password required)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://10-wheel"

do_install() {
    install -d -m 0750 ${D}${sysconfdir}/sudoers.d
    install -m 0440 ${UNPACKDIR}/10-wheel ${D}${sysconfdir}/sudoers.d/10-wheel
}

# The sudoers.d directory is shared with sudo-lib; rpm requires shared
# directories to have identical attributes, hence the explicit 0750 above
# matching sudo's packaging.
FILES:${PN} = "${sysconfdir}/sudoers.d/10-wheel"

RDEPENDS:${PN} += "sudo"
