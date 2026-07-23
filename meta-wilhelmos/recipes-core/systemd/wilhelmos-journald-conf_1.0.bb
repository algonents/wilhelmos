SUMMARY = "WilhelmOS journald policy (persistent storage with size caps)"
DESCRIPTION = "Drop-in for systemd-journald enabling persistent journal \
storage in /var/log/journal with bounded disk usage. Requires \
VOLATILE_LOG_DIR = 'no' (set in the wilhelmos distro conf) so that \
/var/log is real storage rather than tmpfs."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://10-wilhelmos-persistent.conf"

do_install() {
    install -d ${D}${sysconfdir}/systemd/journald.conf.d
    install -m 0644 ${UNPACKDIR}/10-wilhelmos-persistent.conf \
        ${D}${sysconfdir}/systemd/journald.conf.d/10-wilhelmos-persistent.conf
}

FILES:${PN} = "${sysconfdir}/systemd/journald.conf.d"

RDEPENDS:${PN} += "systemd"
