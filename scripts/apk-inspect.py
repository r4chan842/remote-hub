#!/usr/bin/env python3
"""Static packaging checks for one APK: ABIs, STORED native libraries, 16 KB zip
offsets, ELF LOAD segment alignment and stray desktop binaries.

Usage: apk-inspect.py APK [--expect-abi ABI ...] [--allow-4k LIB ...]
Prints a report and exits non-zero on a packaging error. 4 KB ELF alignment on a
64-bit ABI is an error unless the library is listed with --allow-4k (it is then a
warning: such a third-party library must be rebuilt upstream to load on 16 KB pages).
"""
import argparse
import struct
import sys
import zipfile

PAGE_16K = 16384
DESKTOP_PREFIXES = (
    "com/sun/jna/aix-", "com/sun/jna/darwin", "com/sun/jna/win32-", "com/sun/jna/sunos-",
    "com/sun/jna/freebsd-", "com/sun/jna/openbsd-", "com/sun/jna/dragonflybsd-", "com/sun/jna/linux-",
)
REQUIRED_LIBRARIES = {"libgojni.so", "libremotehub-rdp.so", "libfreerdp3.so", "libsodium.so"}
BITS64 = {"arm64-v8a", "x86_64"}


def local_data_offset(zf: zipfile.ZipFile, info: zipfile.ZipInfo) -> int:
    zf.fp.seek(info.header_offset)
    header = zf.fp.read(30)
    name_len, extra_len = struct.unpack("<HH", header[26:30])
    return info.header_offset + 30 + name_len + extra_len


def min_load_align(data: bytes) -> int:
    if data[:4] != b"\x7fELF":
        return 0
    if data[4] == 2:
        phoff = struct.unpack_from("<Q", data, 32)[0]
        esz, num = struct.unpack_from("<HH", data, 54)
        aligns = [struct.unpack_from("<Q", data, phoff + k * esz + 48)[0]
                  for k in range(num) if struct.unpack_from("<I", data, phoff + k * esz)[0] == 1]
    else:
        phoff = struct.unpack_from("<I", data, 28)[0]
        esz, num = struct.unpack_from("<HH", data, 42)
        aligns = [struct.unpack_from("<I", data, phoff + k * esz + 28)[0]
                  for k in range(num) if struct.unpack_from("<I", data, phoff + k * esz)[0] == 1]
    return min(aligns) if aligns else 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("apk")
    ap.add_argument("--expect-abi", action="append", default=[])
    ap.add_argument("--allow-4k", action="append", default=[])
    args = ap.parse_args()

    errors, warnings = [], []
    with zipfile.ZipFile(args.apk) as zf:
        infos = zf.infolist()
        abis = sorted({i.filename.split("/")[1] for i in infos if i.filename.startswith("lib/") and i.filename.count("/") >= 2})
        print(f"ABIs: {' '.join(abis) or 'none'}")
        if args.expect_abi and abis != sorted(args.expect_abi):
            errors.append(f"expected ABIs {sorted(args.expect_abi)}, found {abis}")
        names = set(zf.namelist())
        for abi in abis:
            for lib in sorted(REQUIRED_LIBRARIES):
                if f"lib/{abi}/{lib}" not in names:
                    errors.append(f"{abi} missing {lib}")
        native_bytes = 0
        for info in infos:
            name = info.filename
            if name.startswith(DESKTOP_PREFIXES):
                errors.append(f"desktop-only binary packaged: {name}")
            if not name.startswith("lib/") or not name.endswith(".so") or name.count("/") < 2:
                continue
            native_bytes += info.compress_size
            abi = name.split("/")[1]
            base = name.rsplit("/", 1)[-1]
            stored = info.compress_type == zipfile.ZIP_STORED
            offset = local_data_offset(zf, info)
            align = min_load_align(zf.read(info))
            print(f"  {name:55s} {info.file_size:>10d} {'STORED' if stored else 'DEFLATED':8s} "
                  f"zip16k={'yes' if offset % PAGE_16K == 0 else 'no '} elf_align={align}")
            if not stored:
                pass  # compressed on purpose (smaller download); extracted at install
            elif offset % PAGE_16K:
                errors.append(f"{name} data offset {offset} is not 16 KB aligned")
            if abi in BITS64 and align < PAGE_16K:
                msg = f"{name} ELF LOAD alignment {align} < 16384 (will not load on 16 KB page devices)"
                (warnings if base in args.allow_4k else errors).append(msg)
        print(f"Native payload: {native_bytes} bytes")
    for w in warnings:
        print(f"WARNING: {w}")
    for e in errors:
        print(f"ERROR: {e}", file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
