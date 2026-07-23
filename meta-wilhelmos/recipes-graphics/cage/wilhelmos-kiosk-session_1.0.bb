SUMMARY = "WilhelmOS kiosk session: cage running the wilhelm imgui demo on tty1"
DESCRIPTION = "Installs the cage-kiosk systemd service that starts the cage \
compositor on tty1 as the dedicated 'kiosk' user (seatd seat management, no \
PAM/logind session) and launches the wilhelm_renderer imgui demo fullscreen. \
A maintenance getty stays on tty2."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://cage-kiosk.service"

S = "${UNPACKDIR}"

inherit systemd useradd features_check

REQUIRED_DISTRO_FEATURES = "wayland opengl"

USERADD_PACKAGES = "${PN}"
GROUPADD_PARAM:${PN} = "-r render; -r seat"
USERADD_PARAM:${PN} = "--system --home /home/kiosk --create-home \
    --shell /sbin/nologin --user-group -G video,input,render,seat kiosk"

do_install() {
    install -Dm0644 ${UNPACKDIR}/cage-kiosk.service \
        ${D}${systemd_system_unitdir}/cage-kiosk.service

    # Maintenance shell stays on tty2 (DESIGN.md) - enable it explicitly
    install -d ${D}${sysconfdir}/systemd/system/getty.target.wants
    ln -sf ${systemd_system_unitdir}/getty@.service \
        ${D}${sysconfdir}/systemd/system/getty.target.wants/getty@tty2.service
}

FILES:${PN} += "${systemd_system_unitdir} ${sysconfdir}/systemd"

SYSTEMD_SERVICE:${PN} = "cage-kiosk.service"

RDEPENDS:${PN} = "cage seatd wilhelm-renderer-demo"
