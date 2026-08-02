SUMMARY = "WilhelmOS SPA session: cage running Cog/WPE WebKit on tty1"
DESCRIPTION = "Installs the cage-spa systemd service that starts the cage \
compositor on tty1 as the dedicated 'kiosk' user and launches Cog (WPE \
WebKit) fullscreen via the wilhelmos-spa-launch script. The SPA itself is \
provided by whichever web-root package the image installs (SPA_APP, \
spa-app.bbclass contract — see docs/SPA-CONTRACT.md). A maintenance getty \
stays on tty2."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://cage-spa.service \
    file://wilhelmos-spa-launch \
    file://wilhelmos-spa-url \
"

S = "${UNPACKDIR}"

# Same deployment knob as the kiosk profile (its rationale in
# wilhelmos-kiosk-session): exported to the session, mapped by the
# launcher to Cog's output device scale factor. "" = no drop-in.
WILHELMOS_UI_SCALE ?= "1.5"

# The SPA to load (§10): the primary deployment model — the web app
# lives on a server and deploys/updates entirely server-side, no image
# rebuild. Default empty: the launcher falls back to the bundled web
# root (/usr/share/spa-app), used by the out-of-box demo image and by
# offline deployments.
WILHELMOS_SPA_URL ?= ""

inherit systemd useradd features_check

REQUIRED_DISTRO_FEATURES = "wayland opengl"

USERADD_PACKAGES = "${PN}"
GROUPADD_PARAM:${PN} = "-r render; -r seat"
USERADD_PARAM:${PN} = "--system --home /home/kiosk --create-home \
    --shell /sbin/nologin --user-group -G video,input,render,seat kiosk"

do_install() {
    install -Dm0644 ${UNPACKDIR}/cage-spa.service \
        ${D}${systemd_system_unitdir}/cage-spa.service
    install -Dm0755 ${UNPACKDIR}/wilhelmos-spa-launch \
        ${D}${libexecdir}/wilhelmos-spa-launch

    # Operator tool (tty2): runtime URL override via /etc drop-in
    install -Dm0755 ${UNPACKDIR}/wilhelmos-spa-url \
        ${D}${bindir}/wilhelmos-spa-url

    # Maintenance shell stays on tty2 (DESIGN.md) - enable it explicitly
    install -d ${D}${sysconfdir}/systemd/system/getty.target.wants
    ln -sf ${systemd_system_unitdir}/getty@.service \
        ${D}${sysconfdir}/systemd/system/getty.target.wants/getty@tty2.service

    # Optional UI-scale drop-in (see WILHELMOS_UI_SCALE above)
    if [ -n "${WILHELMOS_UI_SCALE}" ]; then
        install -d ${D}${systemd_system_unitdir}/cage-spa.service.d
        printf '[Service]\nEnvironment=WILHELMOS_UI_SCALE=%s\n' \
            '${WILHELMOS_UI_SCALE}' \
            > ${D}${systemd_system_unitdir}/cage-spa.service.d/10-ui-scale.conf
        chmod 0644 ${D}${systemd_system_unitdir}/cage-spa.service.d/10-ui-scale.conf
    fi

    # Optional SPA-URL drop-in (see WILHELMOS_SPA_URL above)
    if [ -n "${WILHELMOS_SPA_URL}" ]; then
        install -d ${D}${systemd_system_unitdir}/cage-spa.service.d
        printf '[Service]\nEnvironment=WILHELMOS_SPA_URL=%s\n' \
            '${WILHELMOS_SPA_URL}' \
            > ${D}${systemd_system_unitdir}/cage-spa.service.d/20-spa-url.conf
        chmod 0644 ${D}${systemd_system_unitdir}/cage-spa.service.d/20-spa-url.conf
    fi
}

FILES:${PN} += "${systemd_system_unitdir} ${sysconfdir}/systemd ${libexecdir}"

SYSTEMD_SERVICE:${PN} = "cage-spa.service"

# App-neutral by design (§10): the SPA arrives either as a URL
# (WILHELMOS_SPA_URL, primary model — nothing app-related in the image)
# or as an installed spa-app web-root package (bundled/offline model,
# chosen by the image via SPA_APP). The session therefore does NOT
# depend on the spa-app role; the image enforces that at least one of
# the two mechanisms is configured.
RDEPENDS:${PN} = "cage seatd cog"
