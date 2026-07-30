SUMMARY = "WilhelmOS disk installer"
DESCRIPTION = "wilhelmos-install: writes the WilhelmOS image from the \
wicstore partition of the install medium to a target disk and registers \
a UEFI boot entry, replacing any preinstalled vendor OS."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://wilhelmos-install"

S = "${UNPACKDIR}"

do_install() {
    install -d ${D}${sbindir}
    install -m 0755 ${S}/wilhelmos-install ${D}${sbindir}/wilhelmos-install
}

RDEPENDS:${PN} = "efibootmgr util-linux-lsblk util-linux-sfdisk util-linux-findmnt util-linux-blockdev e2fsprogs-e2fsck e2fsprogs-resize2fs"
