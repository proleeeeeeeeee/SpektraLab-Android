#!/usr/bin/env python3
"""Check packaged ABIs, native ELF load alignment and required attribution."""
# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 proleeeeeeeeee
import argparse
from pathlib import Path
import struct
import re
import zipfile


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    with zipfile.ZipFile(args.apk) as apk:
        libraries = [name for name in apk.namelist() if name.startswith("lib/") and name.endswith(".so")]
        assert libraries, "No native libraries packaged"
        for name in libraries:
            blob = apk.read(name)
            assert not re.search(rb"[A-Za-z]:[/\\]Users[/\\][^/\\]+[/\\]", blob), \
                "A native library contains a local Windows account path: " + name
            assert blob[:6] == b"\x7fELF\x02\x01", "Expected little-endian ELF64: " + name
            offset = struct.unpack_from("<Q", blob, 32)[0]
            stride, count = struct.unpack_from("<HH", blob, 54)
            for index in range(count):
                position = offset + index * stride
                if struct.unpack_from("<I", blob, position)[0] == 1:
                    alignment = struct.unpack_from("<Q", blob, position + 48)[0]
                    assert alignment >= 16384, "Native load segment is not 16 KB compatible: " + name
        for name in ("assets/licenses/LICENSE", "assets/licenses/SPEKTRAFILM_LICENSE.txt",
                     "assets/licenses/Product-profiles-NOTICE.md", "assets/licenses/Android-NOTICE.md",
                     "assets/licenses/LibRaw-LGPL-2.1.txt", "assets/licenses/LibRaw-COPYRIGHT.txt",
                     "assets/engine/io/sRGB.icc",
                     "assets/engine/spektrafilm_constants.bin", "assets/engine/vulkan/spk_take_rgb.spv"):
            assert name in apk.namelist(), "Missing required asset: " + name
        print("ABIs: " + ", ".join(sorted({name.split("/")[1] for name in libraries})))
        print(str(len(libraries)) + " native libraries: 16 KB ELF load alignment verified")
        print("Engine constants, shader and required attribution: present")


if __name__ == "__main__":
    main()
