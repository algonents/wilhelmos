# WilhelmOS validated hardware

The record behind the `genericx86-64` claim: WilhelmOS positions itself as
COTS on generic x86-64 hardware (x86-64-v3 baseline), and this file is the
evidence — one entry per physical configuration the kiosk image has
actually been brought up and exercised on. An entry names the exact
hardware, firmware setup, image version, and what was verified, so
"supported" always traces to a test, not an assumption.

Hardware acceptance criteria (checked before purchase):

- **CPU implements x86-64-v3** (AVX2 et al.) — the image is compiled for
  it and will not boot on older microarchitectures.
- **UEFI boot from USB**, Secure Boot disabled (image is unsigned).
- **Integrated GPU only** — the certified display chain assumes a single
  DRM/KMS device; hybrid/discrete graphics topologies are out of scope.
- GPU and NIC firmware covered by the image's `linux-firmware-*` set
  (Intel i915/xe, AMD amdgpu, Realtek NICs as of the AMD-support change).

## Validation checklist (run per entry)

1. Flash the release image to USB (`docs/BOOT.md`), boot with Secure Boot
   disabled.
2. Kiosk session reaches the application fullscreen on tty1 (native DRM
   render node, not the llvmpipe fallback — check the cage/journald log).
3. Maintenance getty on tty2 behind login.
4. Wired network: link up, client reaches the configured server
   (`SKY_GUARD_*_URL` env or equivalent).
5. `systemctl stop/restart cage-kiosk` behaves (clean shutdown, session
   returns); power-loss reboot returns to the kiosk unattended.
6. Sustained run (30+ min) with FPS observation recorded.

## Validated configurations

| # | Machine | CPU / iGPU | NIC | BIOS notes | Image version | Date | Result / notes |
|---|---------|------------|-----|------------|---------------|------|----------------|
| 1 | Development workstation (reference) | Intel (i915/xe path) | — | — | (continuous) | 2026 | Primary development machine; not independent evidence |
| 2 | GMKtec NucBox M5 Ultra | AMD Ryzen 7 7730U / Radeon Vega 8 "Barcelo" (`amdgpu` `1002:15E7`, `green_sardine_*` firmware) | 2× Realtek RTL8125B 2.5GbE (`r8169`, `rtl8125b-2.fw`) | BIOS M5 Ultra 1.06, Secure Boot off | 0.1.0, kiosk image 20260727194924 | 2026-07-27 | First AMD + first non-development configuration. Kiosk fullscreen on native hw GL (radeonsi/renoir/ACO, 4K@60 over DP); tty2 getty with readable console font; clean `shutdown` from tty2. Validation surfaced three platform fixes, all in-tree: AMD/Realtek firmware set (bdeb82c), compiled-in fbcon font, `TimeoutStopSec` on cage-kiosk. Checklist items 4 (network service reach) and 6 (30-min sustained run) still pending |
