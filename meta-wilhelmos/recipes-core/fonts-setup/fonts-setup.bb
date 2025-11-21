SUMMARY = "WilhelmOS early console font setup (BusyBox init)"
LICENSE = "CLOSED"

SRC_URI = "file://font-setup.sh"

S = "${WORKDIR}"

inherit update-rc.d

INITSCRIPT_NAME = "font-setup"
INITSCRIPT_PARAMS = "start 02 S ."

do_install() {
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${WORKDIR}/font-setup.sh ${D}${sysconfdir}/init.d/font-setup
}