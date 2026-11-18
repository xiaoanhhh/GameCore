#!/usr/bin/env python3
"""首次配置同机平台和TLS节点；隔离旧Tank的current/用户/配置，秘密仅在目标主机生成。"""
import argparse
import ipaddress
import os
from pathlib import Path
import re
import secrets
import subprocess


def public_ip(value):
    address = ipaddress.IPv4Address(value)
    if not address.is_global:
        raise ValueError('A public IPv4 address is required')
    return str(address)


def release_directory(value):
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,63}', value) or '..' in value:
        raise ValueError('Invalid immutable release ID')
    return Path('/opt/gamecore/releases') / value


def run(*args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def directory(path, owner, mode=0o750):
    import pwd
    target = Path(path)
    if target.is_symlink():
        raise ValueError('Refusing symlink directory: ' + str(target))
    target.mkdir(parents=True, exist_ok=True)
    user = pwd.getpwnam(owner)
    os.chown(target, user.pw_uid, user.pw_gid)
    target.chmod(mode)


def write_new(path, text, group='root', mode=0o640):
    import grp
    target = Path(path)
    with target.open('x', encoding='utf-8', newline='\n') as output:
        os.chmod(target, mode)
        os.chown(target, 0, grp.getgrnam(group).gr_gid)
        output.write(text)


def node_unit(template):
    replacements = {
        '/opt/gamecore/current': '/opt/gamecore/online',
        '/etc/gamecore/': '/etc/gamecore-online/',
        '/var/lib/gamecore': '/var/lib/gamecore-online',
        '/var/log/gamecore': '/var/log/gamecore-online',
        'User=gamecore\n': 'User=gamecore-online\n',
        'Group=gamecore\n': 'Group=gamecore-online\n',
        'StateDirectory=gamecore\n': 'StateDirectory=gamecore-online\n',
        'LogsDirectory=gamecore\n': 'LogsDirectory=gamecore-online\n',
        'RuntimeDirectory=gamecore\n': 'RuntimeDirectory=gamecore-online\n',
        'SyslogIdentifier=gamecore\n': 'SyslogIdentifier=gamecore-online\n',
    }
    for old, new in replacements.items():
        template = template.replace(old, new)
    return template


def nginx_config(address):
    address = public_ip(address)
    return f'''# IP HTTPS only; account requests never pass over plaintext HTTP.
limit_req_zone $binary_remote_addr zone=gamecore_auth:1m rate=10r/m;
limit_req_zone $binary_remote_addr zone=gamecore_api:1m rate=10r/s;
limit_conn_zone $binary_remote_addr zone=gamecore_connections:1m;
server {{
    listen 443 ssl;
    server_name {address};
    ssl_certificate /etc/letsencrypt/live/gamecore-ip/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/gamecore-ip/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:gamecore_tls:1m;
    ssl_session_timeout 10m;
    server_tokens off;
    client_max_body_size 96k;
    client_header_timeout 5s;
    client_body_timeout 5s;
    keepalive_timeout 15s;
    limit_conn gamecore_connections 16;
    limit_req_status 429;
    location /api/internal/ {{ return 403; }}
    location /api/auth/ {{
        limit_req zone=gamecore_auth burst=10 nodelay;
        proxy_pass http://127.0.0.1:8080;
        include /etc/nginx/gamecore-proxy-headers.conf;
    }}
    location /api/ {{
        limit_req zone=gamecore_api burst=20 nodelay;
        proxy_pass http://127.0.0.1:8080;
        include /etc/nginx/gamecore-proxy-headers.conf;
    }}
    location / {{ return 404; }}
}}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--release', required=True)
    parser.add_argument('--public-ip', required=True)
    args = parser.parse_args()
    address, release = public_ip(args.public_ip), release_directory(args.release)
    if os.name != 'posix' or os.geteuid() != 0:
        raise PermissionError('Run on target host as root')
    import grp
    import pwd
    config = Path('/etc/gamecore-online')
    if config.exists() or Path('/opt/gamecore/online').exists():
        raise FileExistsError('Online stack already exists; do not overwrite credentials/data')
    if not (release / 'platform/platform-service.jar').is_file() or not (release / 'server/lib').is_dir():
        raise ValueError('Install and verify immutable release first')
    if not Path('/etc/letsencrypt/live/gamecore-ip/fullchain.pem').is_file():
        raise ValueError('Obtain trusted IP certificate before configuring TLS')
    for account in ('gamecore-online', 'gamecore-platform'):
        try:
            existing = pwd.getpwnam(account)
            if existing.pw_shell != '/usr/sbin/nologin' or existing.pw_uid == 0:
                raise ValueError('Unsafe existing service user')
        except KeyError:
            run('useradd', '--system', '--user-group', '--home-dir', '/var/lib/' + account,
                '--shell', '/usr/sbin/nologin', account)
        for parent in ('/var/lib/', '/var/log/'):
            directory(parent + account, account)
    directory(config, 'root')
    os.chown(config, 0, grp.getgrnam('gamecore-online').gr_gid)
    directory(config / 'tls', 'root')
    os.chown(config / 'tls', 0, grp.getgrnam('gamecore-online').gr_gid)
    # 口令为URL安全随机值，不经shell、参数或stdout；只交给本机psql标准输入与受保护配置。
    key, password = secrets.token_urlsafe(48), secrets.token_urlsafe(36)
    sql = "CREATE ROLE gamecore_platform LOGIN PASSWORD '" + password + "';\nCREATE DATABASE gamecore OWNER gamecore_platform;\n"
    run('runuser', '-u', 'postgres', '--', 'psql', '-X', '--set', 'ON_ERROR_STOP=1', '--dbname', 'postgres',
        input=sql, text=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    write_new(config / 'gamecore.env', 'GAME_SERVER_KEY=' + key + '\n', 'gamecore-online')
    write_new(config / 'platform.env', f'''GAME_SERVER_KEY={key}
GAME_SERVER_ID=local-1
GAME_PUBLIC_HOST={address}
GAME_PUBLIC_PORT=7201
GAME_PUBLIC_TLS=true
PLATFORM_BIND=127.0.0.1
PLATFORM_PORT=8080
PLATFORM_DB_URL=jdbc:postgresql://127.0.0.1:5432/gamecore
PLATFORM_DB_USER=gamecore_platform
PLATFORM_DB_PASSWORD={password}
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=4
SERVER_TOMCAT_THREADS_MAX=16
''', 'gamecore-platform')
    server = (release / 'deploy/config/cloud.properties').read_text()
    server = server.replace('maxRooms=4', 'maxRooms=2').replace('maxConnections=16', 'maxConnections=8')
    server = server.replace('/etc/gamecore/tls/', '/etc/gamecore-online/tls/current/')
    server = server.replace('/var/lib/gamecore/', '/var/lib/gamecore-online/')
    write_new(config / 'server.properties', server, 'gamecore-online')
    online = node_unit((release / 'deploy/systemd/gamecore.service').read_text())
    write_new('/etc/systemd/system/gamecore-online.service', online, mode=0o644)
    platform = (release / 'deploy/systemd/gamecore-platform.service').read_text()
    platform = platform.replace('/opt/gamecore/current', '/opt/gamecore/online')
    platform = platform.replace('/etc/gamecore/platform.env', '/etc/gamecore-online/platform.env')
    write_new('/etc/systemd/system/gamecore-platform.service', platform, mode=0o644)
    # 平台用户只能读取自己的环境文件；父目录允许遍历，但不能列目录或读取节点凭据。
    config.chmod(0o751)
    write_new('/etc/nginx/gamecore-proxy-headers.conf', '''proxy_connect_timeout 2s;
proxy_read_timeout 10s;
proxy_set_header Host $host;
proxy_set_header X-Forwarded-Proto https;
proxy_set_header X-Forwarded-For $remote_addr;
''', mode=0o644)
    write_new('/etc/nginx/sites-available/gamecore-platform', nginx_config(address), mode=0o644)
    Path('/etc/nginx/sites-enabled/gamecore-platform').symlink_to('/etc/nginx/sites-available/gamecore-platform')
    Path('/opt/gamecore/online').symlink_to(release)
    run('nginx', '-t')
    run('systemctl', 'daemon-reload')
    print('CLOUD_IP_PLATFORM_CONFIGURED (secrets remain on server)')


if __name__ == '__main__':
    main()
