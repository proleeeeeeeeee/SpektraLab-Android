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
    repositories = [(root, "")]
    revisions = []
    # Include every pinned dependency, including LibRaw. GitHub's automatic
    # source ZIP omits gitlink contents and is not complete corresponding source.
    for repository, directory in repositories:
        for entry in git(repository,"ls-tree","-r","HEAD").decode().splitlines():
            metadata, path = entry.split("\t",1)
            mode, kind, pin = metadata.split()
            if mode != "160000":
                continue
            dependency = repository / path
            if git(dependency,"rev-parse","HEAD").decode().strip() != pin:
                raise SystemExit("Dependency does not match its committed gitlink: " + directory + path)
            if git(dependency,"status","--porcelain","--untracked-files=no").strip():
                raise SystemExit("Dependency has uncommitted changes: " + directory + path)
            repositories.append((dependency,directory+path+"/"))
            revisions.append(directory+path+" "+pin)
    if git(root, "status", "--porcelain", "--untracked-files=no").strip():
        raise SystemExit("Commit tracked changes before packaging matching source.")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    prefix = "SpektraLab-Android/"
    with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED) as output:
        for repository, directory in repositories:
            archive = git(repository, "archive", "--format=zip", "HEAD")
            with zipfile.ZipFile(io.BytesIO(archive)) as source:
                for entry in source.infolist():
                    if not entry.is_dir():
                        target = zipfile.ZipInfo(prefix + directory + entry.filename, entry.date_time)
                        target.create_system = entry.create_system
                        target.external_attr = entry.external_attr
                        target.compress_type = zipfile.ZIP_DEFLATED
                        output.writestr(target, source.read(entry))
        output.writestr(prefix + "SOURCE_REVISION.txt",
                        git(root, "rev-parse", "HEAD").decode() + "\n".join(revisions) + "\n")
    print(args.output)


if __name__ == "__main__":
    main()
