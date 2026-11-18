"""隔离切版控制流：只在临时目录替换固定部署根和root检查，systemd命令为显式测试桩。"""
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


@unittest.skipUnless(os.name=="posix","Requires local Linux bash/symlinks")
class ActivationTest(unittest.TestCase):
    def test_success_start_failure_rollback_and_preflight_failure(self):
        source=Path(__file__).parents[1]
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);base=root/"app";bin=root/"bin";bin.mkdir();base.mkdir()
            for version in ("old","good","bad"):
                release=base/"releases"/version;release.mkdir(parents=True)
                (release/"RELEASE").write_text(version+"\n")
                scripts=release/"deploy/scripts";scripts.mkdir(parents=True)
                (scripts/"verify-release.py").write_bytes((source/"scripts/verify-release.py").read_bytes())
                (scripts/"start.sh").write_text("#!/bin/bash\nexit 0\n");(scripts/"start.sh").chmod(0o755)
                files=sorted(p for p in release.rglob("*") if p.is_file())
                (release/"SHA256SUMS").write_text("".join(hashlib.sha256(p.read_bytes()).hexdigest()+"  "+p.relative_to(release).as_posix()+"\n" for p in files))
            (base/"current").symlink_to(base/"releases/old")
            # 仅测试副本改两处环境约束；仓库的正式脚本仍要求root和/opt/gamecore。
            script=root/"activate.sh";text=(source/"scripts/activate.sh").read_text().replace("base=/opt/gamecore","base='"+str(base)+"'").replace("[[ $EUID == 0 && $# == 1","[[ $# == 1")
            script.write_text(text)
            (bin/"systemd-run").write_text('#!/bin/bash\necho preflight >> "$MOCK_LOG"\n[[ ${FAIL_PREFLIGHT:-0} == 0 ]]\n')
            (bin/"systemctl").write_text('#!/bin/bash\necho "$*" >> "$MOCK_LOG"\nif [[ $1 == start && $(readlink "$MOCK_BASE/current") == */bad ]]; then exit 1; fi\n')
            for file in bin.iterdir():file.chmod(0o755)
            env=os.environ.copy();env.update(PATH=str(bin)+":"+env["PATH"],MOCK_BASE=str(base),MOCK_LOG=str(root/"calls"))
            def call(version):return subprocess.run(["bash",str(script),version],env=env,text=True,capture_output=True)
            result=call("good");self.assertEqual(0,result.returncode,result.stderr);self.assertEqual("good",(base/"current").resolve().name)
            result=call("bad");self.assertNotEqual(0,result.returncode);self.assertIn("ROLLED_BACK_TO",result.stderr);self.assertEqual("good",(base/"current").resolve().name)
            (root/"calls").write_text("");env["FAIL_PREFLIGHT"]="1";result=call("old");self.assertNotEqual(0,result.returncode);self.assertEqual("good",(base/"current").resolve().name);self.assertEqual("preflight\n",(root/"calls").read_text())
            self.assertNotEqual(0,call("../bad").returncode)


if __name__=="__main__":unittest.main()
