SUMMARY = "WilhelmOS kiosk session: cage running the kiosk application on tty1"
DESCRIPTION = "Installs the cage-kiosk systemd service that starts the cage \
compositor on tty1 as the dedicated 'kiosk' user (seatd seat management, no \
PAM/logind session) and launches the kiosk application via the stable \
/usr/libexec/kiosk-app path, provided by whichever application package the \
image installs (KIOSK_APP). A maintenance getty stays on tty2."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://cage-kiosk.service"

S = "${UNPACKDIR}"

# UI scaling knob (wilhelmos_kiosk framework, its DESIGN.md §12): installs
# a systemd drop-in exporting WILHELMOS_UI_SCALE to the kiosk application.
# Platform default 1.5 (agreed 2026-07-26: readable on the 4K panels the
# product targets without dwarfing lower-resolution ones). Deployments
# override per panel/viewing distance in their kas/image config; set to ""
# to install no drop-in (framework then falls back to 1.0).
WILHELMOS_UI_SCALE ?= "1.5"

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

    # Optional UI-scale drop-in (see WILHELMOS_UI_SCALE above)
    if [ -n "${WILHELMOS_UI_SCALE}" ]; then
        install -d ${D}${systemd_system_unitdir}/cage-kiosk.service.d
        printf '[Service]\nEnvironment=WILHELMOS_UI_SCALE=%s\n' \
            '${WILHELMOS_UI_SCALE}' \
            > ${D}${systemd_system_unitdir}/cage-kiosk.service.d/10-ui-scale.conf
        chmod 0644 ${D}${systemd_system_unitdir}/cage-kiosk.service.d/10-ui-scale.conf
    fi
}

FILES:${PN} += "${systemd_system_unitdir} ${sysconfdir}/systemd"

SYSTEMD_SERVICE:${PN} = "cage-kiosk.service"

# App-neutral by design (§7): the kiosk application is chosen by the
# image via KIOSK_APP; this package depends only on the session machinery.
RDEPENDS:${PN} = "cage seatd kiosk-app"
