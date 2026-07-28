KAS_FILE ?= kas/qemu-wrynose.yaml

# All build-system state (upstream layer clones, build/, downloads/,
# sstate-cache/) lives outside this repository, in KAS_WORK_DIR.
export KAS_WORK_DIR ?= $(abspath ../wilhelmos-build)

.DEFAULT_GOAL := build
.PHONY: build run shell clean distclean help check-kas

check-kas:
	@command -v kas >/dev/null 2>&1 || \
	  { echo "error: 'kas' not found (install: pip3 install --user kas, need >= 4.0)"; exit 1; }
	@kas --version
	@mkdir -p $(KAS_WORK_DIR)

build: check-kas ## Build the WilhelmOS image via kas
	kas build $(KAS_FILE)

run: check-kas ## Run the built image in QEMU (console-only)
	kas shell $(KAS_FILE) -c 'runqemu qemux86-64 nographic'

shell: check-kas ## Drop into a Yocto dev shell (bitbake, runqemu, etc.)
	kas shell $(KAS_FILE)

clean: ## Remove the build directory (keep downloads/sstate for faster rebuilds)
	rm -rf $(KAS_WORK_DIR)/build

distclean: ## Remove KAS_WORK_DIR entirely (upstream clones, downloads, sstate)
	rm -rf $(KAS_WORK_DIR)

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | \
	  awk 'BEGIN {FS = ":.*?## "} {printf "  %-12s %s\n", $$1, $$2}'
