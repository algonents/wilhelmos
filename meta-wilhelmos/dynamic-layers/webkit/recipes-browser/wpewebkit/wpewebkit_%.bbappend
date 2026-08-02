# WilhelmOS SPA-profile policy for the WPE WebKit engine (DESIGN.md §10).

# Media stack minimized (decision 2026-08-01 was fully off; amended
# 2026-08-02): WPE 2.52 does NOT build with ENABLE_VIDEO=OFF — image
# decoder sources include GStreamer headers unconditionally (upstream
# never CI-builds that combination), so `video` (gstreamer core +
# plugins-base) stays as the minimum the engine accepts. The heavier
# subtrees remain off: mediasource/mediastream (plugins-good/-bad),
# webaudio, and speech-synthesis (needs flite from meta-multimedia,
# a layer WilhelmOS does not carry; no use on a closed display).
PACKAGECONFIG:remove = "mediasource mediastream webaudio speech-synthesis"

# journald: WPE logs into the persistent journal like every other
# platform component. bubblewrap: web-process sandbox (kernel userns +
# seccomp verified present in the WilhelmOS kernel config).
PACKAGECONFIG:append = " journald bubblewrap"

# NOTE: remote-inspector stays enabled during bring-up; its removal is
# Phase D closure work (§10) together with the origin allowlist.

# The recipe's gbm PACKAGECONFIG only declares libdrm, but USE_GBM needs
# libgbm (provided by mesa) in the sysroot — configure fails without it
# ("Could NOT find GBM").
DEPENDS:append = " virtual/libgbm"

# The bubblewrap PACKAGECONFIG declares its helpers as build DEPENDS
# only; at runtime the engine execs the baked-in /usr/bin/bwrap and
# /usr/bin/xdg-dbus-proxy paths and ABORTS if they are missing (found
# the hard way on the GMKtec: cog SIGABRT restart-loop, 2026-08-02).
RDEPENDS:${PN}:append = " bubblewrap xdg-dbus-proxy"
