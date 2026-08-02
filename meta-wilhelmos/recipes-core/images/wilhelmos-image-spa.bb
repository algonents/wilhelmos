SUMMARY = "WilhelmOS SPA kiosk image (cage + Cog/WPE WebKit)"
DESCRIPTION = "wilhelmos-image-base plus the SPA profile stack \
(DESIGN.md §10): boots into the cage compositor running Cog (WPE \
WebKit) fullscreen on tty1, loading the bundled SPA web root, with a \
maintenance getty on tty2. Requires the meta-webkit layer \
(kas/spa.yaml)."

require wilhelmos-image-base.bb

# The SPA (§10 composition contract, docs/SPA-CONTRACT.md). Primary
# model: the image is a universal SPA terminal — set WILHELMOS_SPA_URL
# (and SPA_APP = "") and the web app lives on a server, deployed and
# versioned entirely server-side, never built into the image. Bundled
# model: SPA_APP names a web-root package inheriting spa-app.bbclass
# (offline deployments; the out-of-box default is the reference demo so
# the image always boots to something without a server).
SPA_APP ?= "wilhelmos-spa-demo"
WILHELMOS_SPA_URL ?= ""

IMAGE_INSTALL:append = " cage seatd wilhelmos-spa-session ${SPA_APP}"

# Same bare-metal GPU stack as wilhelmos-image-kiosk (see its comments).
IMAGE_INSTALL:append:genericx86-64 = " kernel-modules \
    linux-firmware-i915 linux-firmware-xe \
    linux-firmware-amdgpu linux-firmware-rtl-nic"

# Fail the build, not the boot: the browser must have something to
# load. If a bundled SPA was selected it must have installed the stable
# web root (-f follows the spa-app.bbclass relative symlink); a
# URL-only image (SPA_APP = "") must configure WILHELMOS_SPA_URL.
spa_app_contract_check() {
    if [ -n "${SPA_APP}" ]; then
        if [ ! -f "${IMAGE_ROOTFS}${datadir}/spa-app/index.html" ]; then
            bbfatal "SPA_APP='${SPA_APP}' did not provide ${datadir}/spa-app/index.html; the SPA recipe must inherit spa-app.bbclass (docs/SPA-CONTRACT.md)"
        fi
    elif [ -z "${WILHELMOS_SPA_URL}" ]; then
        bbfatal "SPA_APP is empty and WILHELMOS_SPA_URL is unset: the image would boot a browser with nothing to load. Set WILHELMOS_SPA_URL (URL model) or SPA_APP (bundled model)."
    fi
}
ROOTFS_POSTPROCESS_COMMAND += "spa_app_contract_check;"

# cage-spa.service is WantedBy=graphical.target
SYSTEMD_DEFAULT_TARGET = "graphical.target"
