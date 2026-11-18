"""三个独立JVM/场景：服务器与两个真实libGDX客户端。只清理本脚本启动的进程。"""
import argparse
import pathlib
import re
import subprocess
import time
import json


def run(options):
    root = pathlib.Path(__file__).resolve().parents[1]
    out = pathlib.Path(options.output).resolve()
    out.mkdir(parents=True, exist_ok=True)
    reports = []
    for game in options.games.split(","):
        if game not in ("snake","tank","fight"): raise ValueError("Unknown acceptance game")
        folder = out / game
        folder.mkdir(exist_ok=True)
        log_path = folder / "server.log"
        processes, logs = [], []
        try:
            log = log_path.open("w", encoding="utf-8"); logs.append(log)
            server = subprocess.Popen([options.java, "-Xmx512m", "-cp", options.server_classpath,
                                       "dev.gamecore.server.GameServerMain", "multi", "--port=0", "--dev=true"],
                                      cwd=root, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
            processes.append(server)
            deadline, port = time.monotonic() + 30, None
            while time.monotonic() < deadline and server.poll() is None:
                match = re.search(r"MULTI_SERVER_READY .*?:(\d+)", log_path.read_text(encoding="utf-8", errors="replace"))
                if match: port = int(match[1]); break
                time.sleep(.05)
            if port is None: raise RuntimeError("Server did not bind: " + log_path.read_text(errors="replace"))
            clients = []
            for index in range(2):
                directory = folder / ("P" + str(index + 1)); directory.mkdir(exist_ok=True)
                client_log = directory / "client.log"; stream = client_log.open("w", encoding="utf-8"); logs.append(stream)
                client = subprocess.Popen([options.java, "-Xmx512m", "-cp", options.client_classpath,
                                           "dev.gamecore.lwjgl3.OnlineMultiSmokeClient", str(directory), str(port), game],
                                          cwd=root / "assets", stdout=stream, stderr=subprocess.STDOUT)
                clients.append((client, directory, client_log)); processes.append(client)
            deadline, fast = time.monotonic() + 95, set()
            while time.monotonic() < deadline:
                if any(p.poll() not in (None, 0) for p, _, _ in clients):
                    raise RuntimeError("GUI client failed; inspect " + str(folder))
                if all(p.poll() is not None for p, _, _ in clients): break
                for round_index in range(2):
                    marker = "GUI_READY_FAST_STEP " + game + " round=" + str(round_index)
                    if round_index not in fast and all(marker in f.read_text(encoding="utf-8", errors="replace") for _, _, f in clients):
                        server.stdin.write("/step " + game + "-1 6000\n"); server.stdin.flush(); fast.add(round_index)
                time.sleep(.05)
            if any(p.poll() is None for p, _, _ in clients): raise TimeoutError("GUI acceptance timeout: " + game)
            for _, directory, _ in clients:
                if "MULTI_GUI_OK" not in (directory / "result.txt").read_text(): raise RuntimeError("Missing GUI success marker")
            for round_index in range(2):
                first, second = [(d / ("final-" + str(round_index) + ".pb")).read_bytes() for _, d, _ in clients]
                if first != second: raise AssertionError("Authoritative final states differ: " + game)

            server.stdin.write("/quit\n"); server.stdin.flush(); server.wait(timeout=15)
            if server.returncode != 0: raise RuntimeError("Server shutdown failed")
            reports.append(dict(game=game, clients=2, independent_jvms=3, final_states_equal=True,
                                render_and_lobby=True, network_terminated=True, stages_disposed=True,
                                accelerated_local_debug_ticks=bool(fast), rounds=2, accelerated_debug_rounds=sorted(fast)))
            print("GUI_ACCEPTANCE_OK " + game, flush=True)
        finally:
            for process in reversed(processes):
                if process.poll() is None: process.terminate()
                try: process.wait(timeout=10)
                except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=5)
            for log in logs: log.close()
    (out / "summary.json").write_text(json.dumps(reports, indent=2), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--java", default="java")
    parser.add_argument("--games", default="snake,tank,fight")
    parser.add_argument("--server-classpath", required=True)
    parser.add_argument("--client-classpath", required=True)
    parser.add_argument("--output", required=True)
    run(parser.parse_args())
