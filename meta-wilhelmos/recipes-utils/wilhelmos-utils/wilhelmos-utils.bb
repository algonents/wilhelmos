SUMMARY = "WilhelmOS Rust utilities"
DESCRIPTION = "A set of small Rust CLI tools for WilhelmOS"
HOMEPAGE = "https://github.com/<you>/wilhelmos-utils"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=f09c2e8fba00ae1812e1e8a5bed59303"

SRC_URI = "git://github.com/algonents/wilhelmos-utils.git;branch=master;protocol=https"

SRCREV = "${AUTOREV}"

S = "${WORKDIR}/git"

inherit cargo

CARGO_BUILD_FLAGS:append = " -p wilhelmos-hello"
CARGO_BUILD_MODE = "--release"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 \
        ${B}/target/${TARGET_SYS}/release/wilhelmos-hello \
        ${D}${bindir}/wilhelmos-hello
}


FILES:${PN} += "${bindir}/wilhelmos-hello"
