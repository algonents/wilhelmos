KAS_FILE ?= kas/qemu-kirkstone.yaml

.PHONY: build run shell clean distclean

## Build the QEMU core-image-minimal image
build:
	kas build $(KAS_FILE)

## Run the built image in QEMU (console-only)
run:
	kas shell $(KAS_FILE) -c 'runqemu qemux86-64 nographic'

## Drop into a Yocto dev shell (bitbake, runqemu, etc.)
shell:
	kas shell $(KAS_FILE)

## Remove the build directory (but keep downloads/sstate for faster rebuilds)
clean:
	rm -rf build/

## Remove everything including shared downloads/sstate caches
distclean: clean
	rm -rf ../downloads ../sstate-cache
