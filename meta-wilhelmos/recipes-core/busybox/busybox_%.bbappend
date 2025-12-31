# Stop the busybox recipe from enabling syslog/klog "mode"
SRC_URI:remove = "file://syslog.cfg file://klogd.cfg"

do_install:append() {
    # Ensure BusyBox doesn't ship its syslog/klog unit files
    rm -f \
      ${D}${systemd_system_unitdir}/busybox-syslog.service \
      ${D}${systemd_system_unitdir}/busybox-klogd.service

    # Clean up now-empty directories (but do NOT attempt to remove /lib itself)
    rmdir --ignore-fail-on-non-empty -p \
      ${D}${systemd_system_unitdir} \
      ${D}${systemd_unitdir} \
      ${D}${nonarch_base_libdir}/systemd \
      2>/dev/null || true
}
