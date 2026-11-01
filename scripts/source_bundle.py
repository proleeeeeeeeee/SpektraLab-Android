#!/usr/bin/env python3
"""Archive committed source, including the exact upstream gitlink contents."""
# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 proleeeeeeeeee
import argparse
import io
from pathlib import Path
import subprocess
import zipfile


def git(root: Path, *args: str) -> bytes:
    return subprocess.check_output(["git", "-C", str(root), *args])


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    pin = git(root, "rev-parse", "HEAD:third_party/spektralab").decode().strip()
    upstream = root / "third_party" / "spektralab"
    if git(upstream, "rev-parse", "HEAD").decode().strip() != pin:
        raise SystemExit("The upstream checkout does not match the committed gitlink.")
    if git(root, "status", "--porcelain", "--untracked-files=no").strip():
        raise SystemExit("Commit tracked changes before packaging matching source.")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    prefix = "SpektraLab-Android/"
    with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED) as output:
        for repository, directory in ((root, ""), (upstream, "third_party/spektralab/")):
            archive = git(repository, "archive", "--format=zip", "HEAD")
            with zipfile.ZipFile(io.BytesIO(archive)) as source:
                for entry in source.infolist():
                    if not entry.is_dir():
                        target = zipfile.ZipInfo(prefix + directory + entry.filename, entry.date_time)
                        target.external_attr = entry.external_attr
                        target.compress_type = zipfile.ZIP_DEFLATED
                        output.writestr(target, source.read(entry))
        output.writestr(prefix + "SOURCE_REVISION.txt",
                        git(root, "rev-parse", "HEAD").decode() + "upstream " + pin + "\n")
    print(args.output)


if __name__ == "__main__":
    main()
