# WilhelmOS – Yocto QEMU Minimal (kas-based)

This repository defines how to build a minimal Yocto-based image for
**WilhelmOS**, currently targeting **QEMU x86_64** using **kas**.

The goal of this setup is to *reproduce* the initial manual Yocto build:

- `MACHINE = "qemux86-64"`
- `DISTRO = "poky"`
- `target = core-image-minimal`
- branch: `kirkstone`

## Prerequisites

- Linux build host (e.g. Ubuntu)
- Python 3
- Yocto build dependencies (gcc, git, xz-utils, etc.)
  - See the official Yocto Project Quick Start for the exact package list.
- kas:
  ```bash
  pip3 install --user kas
