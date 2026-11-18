"""纯Python部署输入边界；真实Nginx/TLS/数据库验收另外运行，不能用渲染测试代替。"""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('ip_platform', ROOT / 'deploy/scripts/configure-ip-platform.py')
DEPLOY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(DEPLOY)


class IpPlatformTest(unittest.TestCase):
    def test_accepts_actual_public_ipv4(self):
        self.assertEqual('8.8.8.8', DEPLOY.public_ip('8.8.8.8'))

    def test_rejects_private_or_injected_addresses(self):
        for address in ('127.0.0.1', '10.0.0.1', '192.168.1.1', '169.254.169.254',
                        '::1', '8.8.8.8; id', '8.8.8.8\nserver {}', 'example.com'):
            with self.subTest(address=address), self.assertRaises(ValueError):
                DEPLOY.nginx_config(address)

    def test_release_cannot_escape_install_root(self):
        self.assertEqual(Path('/opt/gamecore/releases/0.1.0-cloud-20261031'),
                         DEPLOY.release_directory('0.1.0-cloud-20261031'))
        for version in ('../current', '/tmp/other', 'v/other', 'v..old', '', 'v\nservice', 'v' * 65):
            with self.subTest(version=version), self.assertRaises(ValueError):
                DEPLOY.release_directory(version)

    def test_new_node_does_not_retarget_legacy_service_paths(self):
        original = (ROOT / 'deploy/systemd/gamecore.service').read_text(encoding='utf-8')
        online = DEPLOY.node_unit(original)
        self.assertIn('ExecStart=/opt/gamecore/online/deploy/scripts/start.sh\n', online)
        self.assertIn('User=gamecore-online\n', online)
        self.assertIn('EnvironmentFile=/etc/gamecore-online/gamecore.env\n', online)
        self.assertNotIn('/opt/gamecore/current', online)
        self.assertNotIn('/var/lib/gamecore\n', online)
        self.assertEqual(original, (ROOT / 'deploy/systemd/gamecore.service').read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
