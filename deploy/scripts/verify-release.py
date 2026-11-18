#!/usr/bin/env python3
"""验证并展开受信发布包；拒绝目录穿越、链接、特殊文件、额外文件和未列入校验表的内容。"""
import hashlib
import os
from pathlib import Path, PurePosixPath
import re
import sys
import tarfile
import tempfile

MAX_BYTES = 256 * 1024 * 1024
VERSION = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}\Z")


def version(value):
    if not VERSION.fullmatch(value) or ".." in value:
        raise ValueError("Invalid release ID")
    return value


def verify_tree(tree):
    entries = {}
    for line in (tree / "SHA256SUMS").read_text(encoding="utf-8").splitlines():
        digest, name = line.split("  ", 1)
        path = PurePosixPath(name)
        if not re.fullmatch(r"[0-9a-f]{64}", digest) or path.is_absolute() or ".." in path.parts or "\\" in name or name in entries:
            raise ValueError("Invalid manifest")
        file = tree / name
        if not file.is_file() or file.is_symlink() or hashlib.sha256(file.read_bytes()).hexdigest() != digest:
            raise ValueError("Manifest mismatch: " + name)
        entries[name] = digest
    actual = set()
    for file in tree.rglob("*"):
        if file.is_symlink() or not (file.is_file() or file.is_dir()):
            raise ValueError("Links/special files are forbidden")
        if file.is_file() and file != tree / "SHA256SUMS":
            actual.add(file.relative_to(tree).as_posix())
    if actual != set(entries):
        raise ValueError("Missing or extra files")
    return version((tree / "RELEASE").read_text(encoding="utf-8").strip())


def install(archive, expected, releases):
    if not re.fullmatch(r"[0-9a-fA-F]{64}", expected) or archive.stat().st_size > MAX_BYTES:
        raise ValueError("Invalid archive/checksum")
    if hashlib.sha256(archive.read_bytes()).hexdigest() != expected.lower():
        raise ValueError("Archive checksum mismatch")
    releases.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".staging-", dir=releases) as temp:
        temp = Path(temp)
        with tarfile.open(archive, "r:gz") as tar:
            members = tar.getmembers()
            if len(members) > 4000 or sum(m.size for m in members) > MAX_BYTES:
                raise ValueError("Archive limits")
            names = set()
            for member in members:
                path = PurePosixPath(member.name)
                if path.is_absolute() or ".." in path.parts or "\\" in member.name or not path.parts or member.name in names or not (member.isfile() or member.isdir()):
                    raise ValueError("Unsafe archive entry")
                names.add(member.name)
                destination = temp.joinpath(*path.parts)
                if member.isdir():
                    destination.mkdir(parents=True, exist_ok=True)
                else:
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    with tar.extractfile(member) as source, destination.open("xb") as target:
                        import shutil
                        shutil.copyfileobj(source, target)
                    destination.chmod(0o644)
        roots = list(temp.iterdir())
        if len(roots) != 1 or not roots[0].is_dir():
            raise ValueError("Expected one release directory")
        tree = roots[0]
        release = verify_tree(tree)
        if tree.name != "gamecore-" + release or (releases / release).exists():
            raise ValueError("Existing/mismatched release ID")
        for path in tree.rglob("*"):
            if path.is_dir():
                path.chmod(0o755)
        for script in (tree / "deploy/scripts").glob("*.sh"):
            script.chmod(0o755)
        for script in (tree / "server/bin").iterdir():
            script.chmod(0o755)
        tree.chmod(0o755)
        os.rename(tree, releases / release)
        return release


if __name__ == "__main__":
    try:
        if len(sys.argv) == 3 and sys.argv[1] == "--tree":
            print(verify_tree(Path(sys.argv[2])))
        elif len(sys.argv) == 4:
            print(install(Path(sys.argv[1]), sys.argv[2], Path(sys.argv[3])))
        else:
            raise ValueError("Usage: verify-release.py ARCHIVE EXPECTED_SHA256 RELEASES | --tree DIRECTORY")
    except (ValueError, OSError, tarfile.TarError) as error:
        sys.exit("Release rejected: " + str(error))
