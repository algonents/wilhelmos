SUMMARY = "Modular Wayland compositor library"
DESCRIPTION = "wlroots provides backends, renderers and protocol \
implementations for building Wayland compositors. Packaged for WilhelmOS \
as the base of the cage kiosk compositor: DRM and libinput backends only, \
GLES2 renderer, no XWayland."
HOMEPAGE = "https://gitlab.freedesktop.org/wlroots/wlroots"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=89e064f90bcb87796ca335cbd2ce4179"

SRC_URI = "git://gitlab.freedesktop.org/wlroots/wlroots.git;protocol=https;branch=0.19"
SRCREV = "88a869855742281c98c22cab9641b317b8d065ef"

DEPENDS = " \
    wayland-native \
    hwdata-native \
    wayland \
    wayland-protocols \
    libdrm \
    virtual/egl \
    virtual/libgles2 \
    virtual/libgbm \
    libinput \
    libxkbcommon \
    pixman \
    seatd \
    libdisplay-info \
    udev \
"

inherit meson pkgconfig features_check

REQUIRED_DISTRO_FEATURES = "wayland opengl"

EXTRA_OEMESON = " \
    -Dbackends=drm,libinput \
    -Drenderers=gles2 \
    -Dallocators=gbm \
    -Dxwayland=disabled \
    -Dsession=enabled \
    -Dexamples=false \
    -Dcolor-management=disabled \
    -Dlibliftoff=disabled \
    -Dxcb-errors=disabled \
"

# wlroots ships an unversioned library (SONAME = libwlroots-0.19.so), which
# would otherwise be packaged into ${PN}-dev and break runtime dependents.
FILES_SOLIBSDEV = ""
FILES:${PN} += "${libdir}/libwlroots-0.19.so"
