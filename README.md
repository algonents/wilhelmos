# WilhelmOS

Minimal Linux distribution built with the Yocto Project (wrynose, 6.0 LTS), targeting
QEMU x86-64 and bare-metal x86-64.

WilhelmOS aims to be a **pre-hardened COTS platform for AL3–AL5 CNS/ATM ground
equipment** under **ED-109A**, reducing the system integrator's certification
burden with a reproducible build, SBOM output, and hardening evidence. See
[docs/DESIGN.md](docs/DESIGN.md) for the phased roadmap.

- `MACHINE = "qemux86-64"`
- `DISTRO = "wilhelmos"` (version 0.1.0)
- Image: `wilhelmos-image-base`
- Upstream layers pinned to exact wrynose commit SHAs (see
  `kas/qemu-wrynose.yaml`)

## Prerequisites

- Linux build host (e.g. Ubuntu, RHEL)
- Python 3
- Yocto build dependencies (gcc, git, xz-utils, etc.)
  - See the official Yocto Project Quick Start for the exact package list.
- kas ≥ 4.0:

  ```bash
  pip3 install --user kas
  ```

### RHEL 10 host notes

Verified on RHEL 10.2 (bitbake warns that this host is not officially
validated for this Yocto release; the build works with the packages below):

```bash
sudo dnf install -y chrpath lz4 rpcgen perl
```

- `chrpath`, `lz4` (`lz4c`), `rpcgen` are on bitbake's required `HOSTTOOLS`
  list but not in a default RHEL install (`rpcgen` comes from AppStream).
- The full `perl` metapackage is required — RHEL's minimal `perl-interpreter`
  lacks core modules (e.g. `open.pm`), which breaks libxcrypt's configure
  with a misleading `bad value 'all' for --enable-hashes` error.
- RHEL has no `pip3` by default; install kas in a venv instead:

  ```bash
  python3 -m venv ~/.local/share/kas-venv
  ~/.local/share/kas-venv/bin/pip install kas
  export PATH="$HOME/.local/share/kas-venv/bin:$PATH"
  ```

- `runqemu` needs root to set up TAP networking; without it, append `slirp`
  for user-mode networking (`runqemu qemux86-64 nographic slirp`).

## Build

```bash
make build          # kas build kas/qemu-wrynose.yaml
```

Downloads and sstate are shared one level above the checkout
(`../downloads`, `../sstate-cache`) to speed up rebuilds.

### Debug image variant

Builds the same image with verbose console/systemd boot logging on the kernel
cmdline (useful when dd'ing the `.wic` to USB for bare-metal debugging):

```bash
kas build kas/qemu-wrynose.yaml:kas/debug.yaml
```

## Run in QEMU

```bash
make run            # runqemu qemux86-64 nographic
```

Log in on the serial console as **`wilhelmos`** / **`wilhelmos`**.

> **Dev-only credential.** This default user and password are baked in for
> development convenience only. Production images must override
> `EXTRA_USERS_PARAMS` (see `meta-wilhelmos/conf/distro/wilhelmos.conf`).
> Root is locked. `sudo` prompts for the user's password (wheel group).

## Make targets

Run `make help` for the list: `build`, `run`, `shell`, `clean`, `distclean`.

## Reproducibility & SBOM

- Upstream layer revisions are pinned in `kas/qemu-wrynose.yaml`. To update
  a pin: `git ls-remote <repo-url> refs/heads/wrynose` and replace the
  `commit:` value.
- Every build produces an SPDX SBOM:
  `build/tmp/deploy/images/qemux86-64/wilhelmos-image-base-qemux86-64.spdx.tar.zst`
- Package/image manifests are tracked via buildhistory under
  `build/buildhistory` (a local git repo).

## Repository layout

```
kas/                  kas build configs (qemu-wrynose.yaml + debug overlay)
meta-wilhelmos/       custom Yocto layer (distro, image, recipes, wic layout)
scripts/              helper scripts
docs/                 design documentation
```

## License

The WilhelmOS build metadata (recipes, configuration) is licensed under the
[MIT License](LICENSE). The bundled Terminus console font is licensed under
the SIL Open Font License 1.1.
