# WilhelmOS – QEMU Minimal (kas-based)

This repository defines how to build a minimal image for
**WilhelmOS**, currently targeting **QEMU x86_64** using **kas**.

- `MACHINE = "qemux86-64"`
- `DISTRO = "wilhelmos"`
- `target = wilhelmos-image-base`
-  branch: `kirkstone`

## Prerequisites

- Linux build host (e.g. Ubuntu)
- Python 3
- Yocto build dependencies (gcc, git, xz-utils, etc.)
  - See the official Yocto Project Quick Start for the exact package list.
- kas:
  ```bash
  pip3 install --user kas
