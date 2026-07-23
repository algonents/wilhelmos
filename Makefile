KAS_FILE ?= kas/qemu-kirkstone.yaml

.DEFAULT_GOAL := build
.PHONY: build run shell clean distclean help check-kas

check-kas:
	@command -v kas >/dev/null 2>&1 || \
	  { echo "error: 'kas' not found (install: pip3 install --user kas, need >= 4.0)"; exit 1; }
	@kas --version

build: check-kas ## Build the WilhelmOS image via kas
	kas build $(KAS_FILE)

run: check-kas ## Run the built image in QEMU (console-only)
	kas shell $(KAS_FILE) -c 'runqemu qemux86-64 nographic'

shell: check-kas ## Drop into a Yocto dev shell (bitbake, runqemu, etc.)
	kas shell $(KAS_FILE)

clean: ## Remove the build directory (keep downloads/sstate for faster rebuilds)
	rm -rf build/

distclean: clean ## Remove build/ plus the shared ../downloads and ../sstate-cache
	rm -rf ../downloads ../sstate-cache

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | \
	  awk 'BEGIN {FS = ":.*?## "} {printf "  %-12s %s\n", $$1, $$2}'
