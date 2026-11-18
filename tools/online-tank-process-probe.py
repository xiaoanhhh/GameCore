"""真实服务器与两个独立libGDX客户端JVM；Python仅编排进程/坏帧，不参与游戏规则。"""
import os
from pathlib import Path
import queue
import re
import socket
import struct
import subprocess
import sys
import threading
import time

ROOT = Path.cwd()
REPORT = Path(sys.argv[1])
WORKDIR = REPORT / "empty-working-directory"
WORKDIR.mkdir(parents=True, exist_ok=True)
CLASSPATH = (REPORT / "classpath.txt").read_text(encoding="utf-8")
SERVER_CP = sys.argv[2] if len(sys.argv) > 2 else str(ROOT / "game-server/build/install/GameCore-TankServer/lib/*")
processes = []
fault_listener = socket.socket()
fault_listener.bind(("127.0.0.1", 0))
fault_listener.listen(1)
fault_listener.settimeout(280)
fault_port = fault_listener.getsockname()[1]
fault_done = threading.Event()
fault_errors = []


def fault_server():
    try:
        with fault_listener.accept()[0] as client:
            client.settimeout(8)
            header = client.recv(4)
            assert len(header) == 4
            length = struct.unpack("!I", header)[0]
            assert 0 < length <= 16384
            remaining = length
            while remaining:
                data = client.recv(remaining)
                assert data
                remaining -= len(data)
            client.sendall(struct.pack("!I", 16385))
            assert client.recv(1) == b""
        fault_done.set()
    except Exception as error:
        fault_errors.append(repr(error))


threading.Thread(target=fault_server, daemon=True).start()


class Program:
    def __init__(self, label, cp, main, *arguments):
        self.label = label
        self.lines = queue.Queue(maxsize=256)
        self.log = (REPORT / f"{label}-process.txt").open("w", encoding="utf-8")
        self.process = subprocess.Popen(
            ["java", "-cp", cp, main, *arguments], cwd=WORKDIR,
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, encoding="utf-8", errors="replace",
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        processes.append(self)
        threading.Thread(target=self._read, daemon=True).start()

    def _read(self):
        for line in self.process.stdout:
            self.log.write(line)
            self.log.flush()
            self.lines.put(line.rstrip())

    def expect(self, pattern, seconds=40):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            try:
                line = self.lines.get(timeout=min(1, max(.01, deadline - time.monotonic())))
            except queue.Empty:
                assert self.process.poll() is None, (self.label, "early exit", self.process.returncode)
                continue
            print(f"[{self.label}] {line}", flush=True)
            if line.startswith('Exception in thread "main"'):
                raise AssertionError((self.label, "fatal Java entry point", line))
            if re.search(pattern, line):
                return line
        raise AssertionError((self.label, "timeout", pattern))

    def send(self, value):
        print(f"[{self.label} input] {value}", flush=True)
        self.process.stdin.write(value + "\n")
        self.process.stdin.flush()

    def finished(self):
        code = self.process.wait(timeout=15)
        assert code == 0, (self.label, code)
        print(f"[{self.label}] exit=0", flush=True)


try:
    server = Program("server", SERVER_CP, "dev.gamecore.server.GameServerMain", "server", "--port=0")
    port = re.search(r":(\d+) tps=30", server.expect("TANK_SERVER_READY")).group(1)
    first = Program("P1", CLASSPATH, "dev.gamecore.lwjgl3.OnlineTankSmokeClient", str(REPORT / "P1"), port, "P1", str(fault_port))
    first.expect(r"ONLINE_WAITING P1 scenario=battle")
    second = Program("P2", CLASSPATH, "dev.gamecore.lwjgl3.OnlineTankSmokeClient", str(REPORT / "P2"), port, "P2", str(fault_port))
    first_running = first.expect(r"ONLINE_RUNNING name=P1 slot=0", seconds=45)
    second_running = second.expect(r"ONLINE_RUNNING name=P2 slot=1", seconds=45)
    assert first_running.split("match=")[1] == second_running.split("match=")[1]
    first.expect("ONLINE_MATCH_RESULT P1", seconds=205)
    second.expect("ONLINE_MATCH_RESULT P2", seconds=25)
    second.expect("ONLINE_CLIENT_DONE P2")
    second.finished()
    first_result = (REPORT / "P1/match-result.pb").read_bytes()
    assert first_result == (REPORT / "P2/match-result.pb").read_bytes(), "Final Protobuf results disagree"
    first.expect("ONLINE_BATTLE_DONE")
    first.send("GO_CYCLES")
    first.expect("ONLINE_READY_FOR_SERVER_STOP", seconds=70)
    assert fault_done.wait(5), fault_errors
    assert not fault_errors, fault_errors
    server.send("/metrics")
    server.expect(r"METRICS .*failures=0")
    server.send("/quit")
    server.expect("TANK_SERVER_STOPPED")
    server.finished()
    first.expect("ONLINE_CLIENT_DONE P1")
    first.finished()
    for name in ("P1", "P2"):
        assert "ONLINE_TANK_SMOKE_OK" in (REPORT / name / "result.txt").read_text(encoding="utf-8")
    (REPORT / "result.txt").write_text(
        "ONLINE_MULTIPLAYER_OK\nSeparateClientJvms=true\nSameMatchDistinctSlots=true\nFinalResultsByteEqual=true\n"
        "RepeatedNativeWindows=true\nMalformedMessageUiSafe=true\nServerDisconnectUiSafe=true\nResourcesReleased=true\n",
        encoding="utf-8")
    print("ONLINE_MULTIPLAYER_OK", flush=True)
finally:
    fault_listener.close()
    for program in reversed(processes):
        process = program.process
        if process.poll() is None:
            process.terminate()
        try:
            process.wait(timeout=12)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=12)
        program.log.flush()
