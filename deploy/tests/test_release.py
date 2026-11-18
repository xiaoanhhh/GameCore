"""纯本地发布工具测试：真实包验收与恶意路径/链接/改包拒绝，不修改/opt或systemd。"""
import hashlib
import importlib.util
import io
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("release", Path(__file__).parents[1] / "scripts/verify-release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTest(unittest.TestCase):
    def test_real_artifact_and_duplicate_rejected(self):
        artifact = Path(sys.argv[1]) if len(sys.argv) > 1 and sys.argv[1].endswith(".tar.gz") else None
        if artifact is None:
            self.skipTest("Supply real artifact path")
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "releases"
            sha = hashlib.sha256(artifact.read_bytes()).hexdigest()
            identity = release.install(artifact, sha, target)
            self.assertEqual(identity, release.verify_tree(target / identity))
            extra=target/identity/"nested/SHA256SUMS";extra.parent.mkdir();extra.write_text("unlisted")
            with self.assertRaises(ValueError):
                release.verify_tree(target/identity)
            extra.unlink();extra.parent.rmdir()
            with self.assertRaises(ValueError):
                release.install(artifact, sha, target)
            (target / identity / "RELEASE").write_text("corrupt", encoding="utf-8")
            with self.assertRaises(ValueError):
                release.verify_tree(target / identity)

    def test_traversal_links_special_and_hash_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            for name, kind in [("../escape",tarfile.REGTYPE),("/escape",tarfile.REGTYPE),("x/link",tarfile.SYMTYPE),("x/hard",tarfile.LNKTYPE),("x/dev",tarfile.CHRTYPE)]:
                archive = base / "bad.tar.gz"
                with tarfile.open(archive,"w:gz") as tar:
                    entry = tarfile.TarInfo(name)
                    entry.type = kind
                    entry.size = 0
                    tar.addfile(entry, io.BytesIO())
                sha = hashlib.sha256(archive.read_bytes()).hexdigest()
                with self.assertRaises(ValueError):
                    release.install(archive, sha, base / "releases")
                with self.assertRaises(ValueError):
                    release.install(archive, "0"*64, base / "releases")


if __name__ == "__main__":
    unittest.main(argv=[sys.argv[0]])
