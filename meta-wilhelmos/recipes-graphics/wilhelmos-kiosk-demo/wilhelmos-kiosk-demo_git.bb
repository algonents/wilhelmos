SUMMARY = "WilhelmOS reference kiosk application"
DESCRIPTION = "Fullscreen wilhelm_renderer + Dear ImGui demo application: \
validates the WilhelmOS graphical kiosk stack end to end and serves as \
the worked packaging example for integrator kiosk applications."
HOMEPAGE = "https://github.com/algonents/wilhelmos-kiosk-demo"
# MIT (crates + Dear ImGui) with statically linked vendored GLFW (Zlib) and
# FreeType (FTL)
LICENSE = "MIT & Zlib & FTL"
LIC_FILES_CHKSUM = "file://LICENSE;md5=b6da98d9ba2b3775998e4e30a3ea717e"

inherit cargo cargo-update-recipe-crates pkgconfig features_check

REQUIRED_DISTRO_FEATURES = "wayland opengl"

SRC_URI = "git://github.com/algonents/wilhelmos-kiosk-demo.git;protocol=https;branch=master"
# v0.1.0 tag
SRCREV = "f5b2d85357befd5f0b213f11a607853493c0dd71"

PV = "0.1.0"

require ${BPN}-crates.inc

# cmake for the vendored GLFW/FreeType/ImGui native builds; wayland +
# libxkbcommon headers for the GLFW Wayland backend; GL/EGL from mesa.
DEPENDS = " \
    cmake-native \
    ninja-native \
    wayland-native \
    wayland \
    wayland-protocols \
    libxkbcommon \
    virtual/egl \
"

# Wayland-only kiosk target: drop the GLFW X11 backend (and with it the
# need for X11 headers in the sysroot).
export GLRENDERER_BUILD_X11 = "OFF"
export GLRENDERER_LINK_GL = "OFF"

# §7 composition contract: exactly one installed package provides the
# kiosk application at the stable path cage-kiosk.service execs.
PROVIDES = "virtual/kiosk-app"
RPROVIDES:${PN} = "kiosk-app"

do_install() {
    install -Dm0755 ${B}/target/${CARGO_TARGET_SUBDIR}/wilhelmos-kiosk-demo \
        ${D}${bindir}/wilhelmos-kiosk-demo
    install -d ${D}${libexecdir}
    ln -s ${bindir}/wilhelmos-kiosk-demo ${D}${libexecdir}/kiosk-app
}

# GLFW loads its platform libraries with dlopen at runtime; none of these
# appear as DT_NEEDED, so shlibdeps cannot discover them.
RDEPENDS:${PN} = " \
    wayland \
    libxkbcommon \
    libglvnd \
    libegl-mesa \
    libgbm \
    mesa-megadriver \
"
