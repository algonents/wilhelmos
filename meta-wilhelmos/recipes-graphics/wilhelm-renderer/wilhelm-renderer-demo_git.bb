SUMMARY = "wilhelm_renderer_imgui fullscreen demo (kiosk GPU-stack validation app)"
DESCRIPTION = "Builds the demo_kiosk example from wilhelm_renderer_imgui \
against the feat/kiosk-validation branches of the wilhelm_renderer crates. \
Validates that applications built with the wilhelm rendering stack (vendored \
GLFW 3.4 Wayland backend + OpenGL 3.3 core + Dear ImGui) run on WilhelmOS."
HOMEPAGE = "https://github.com/algonents/wilhelm_renderer_imgui"
# MIT (crates + Dear ImGui) with statically linked vendored GLFW (Zlib) and
# FreeType (FTL)
LICENSE = "MIT & Zlib & FTL"
LIC_FILES_CHKSUM = " \
    file://LICENSE;md5=b6da98d9ba2b3775998e4e30a3ea717e \
    file://cpp/imgui/LICENSE.txt;md5=5ef20940e74d064639c66f32813613b0 \
"

inherit cargo cargo-update-recipe-crates pkgconfig features_check

REQUIRED_DISTRO_FEATURES = "wayland opengl"

# Entry 1: the imgui crate (S = UNPACKDIR/BP). Entries 2+3: the renderer
# workspace and its sys member from the same repo/rev; name= + destsuffix=
# make cargo_common emit [patch] path overrides for both. The sys checkout is
# nested inside the workspace checkout so cargo resolves one canonical
# wilhelm_renderer_sys directory (links = "wilhelm_renderer" collision
# otherwise).
SRC_URI = " \
    git://github.com/algonents/wilhelm_renderer_imgui.git;protocol=https;branch=feat/kiosk-validation;name=imgui \
    git://github.com/algonents/wilhelm_renderer.git;protocol=https;branch=feat/kiosk-validation;name=wilhelm_renderer;destsuffix=wr-deps/wilhelm_renderer;type=git-dependency \
    git://github.com/algonents/wilhelm_renderer.git;protocol=https;branch=feat/kiosk-validation;name=wilhelm_renderer_sys;destsuffix=wr-deps/wilhelm_renderer/wilhelm_renderer_sys;subpath=wilhelm_renderer_sys;type=git-dependency \
"
SRCREV_imgui = "28b9d6215398dfdbd0a4c40c4fc3a063e9e2bc50"
SRCREV_wilhelm_renderer = "77665f9c66e37676eb5cc11a20f630bc8bbb01b1"
SRCREV_wilhelm_renderer_sys = "77665f9c66e37676eb5cc11a20f630bc8bbb01b1"
SRCREV_FORMAT = "imgui_wilhelm_renderer"

PV = "0.9.0+git"

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
    virtual/libgl \
    virtual/egl \
"

# Wayland-only kiosk target: drop the GLFW X11 backend (and with it the
# need for X11 headers in the sysroot).
export GLRENDERER_BUILD_X11 = "OFF"

do_compile() {
    oe_cargo_build --example demo_kiosk
}

do_install() {
    install -Dm0755 ${B}/target/${CARGO_TARGET_SUBDIR}/examples/demo_kiosk \
        ${D}${bindir}/wilhelm-imgui-demo
}

# GLFW loads its platform libraries with dlopen at runtime; none of these
# appear as DT_NEEDED, so shlibdeps cannot discover them.
RDEPENDS:${PN} = " \
    wayland \
    libxkbcommon \
    libegl-mesa \
    libgl-mesa \
    libgbm \
    mesa-megadriver \
"
