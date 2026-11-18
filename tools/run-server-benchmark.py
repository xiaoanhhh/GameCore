"""可重复的独立服务器+机器人进程实验。RSS使用OS接口，不新增Python第三方依赖。
Windows预算模式仅约束本脚本新建的server JVM：2逻辑CPU亲和性、Job提交内存上限2GiB。
这不是具有2GiB物理内存的云VM；Linux真实cgroup模式见docs/performance。
"""
import argparse
import ctypes
import ctypes.wintypes as wt
import json
import os
import pathlib
import re
import subprocess
import time


class WindowsBudget:
    def __init__(self, process, enabled):
        self.process = process
        self.k = ctypes.WinDLL("kernel32", use_last_error=True)
        self.k.CloseHandle.argtypes = [wt.HANDLE]
        self.k.GetProcessAffinityMask.argtypes = [wt.HANDLE, ctypes.POINTER(ctypes.c_size_t), ctypes.POINTER(ctypes.c_size_t)]
        self.k.SetProcessAffinityMask.argtypes = [wt.HANDLE, ctypes.c_size_t]
        self.k.CreateJobObjectW.argtypes = [ctypes.c_void_p, wt.LPCWSTR]; self.k.CreateJobObjectW.restype = wt.HANDLE
        self.k.SetInformationJobObject.argtypes = [wt.HANDLE, ctypes.c_int, ctypes.c_void_p, wt.DWORD]
        self.k.QueryInformationJobObject.argtypes = [wt.HANDLE, ctypes.c_int, ctypes.c_void_p, wt.DWORD, ctypes.c_void_p]
        self.k.AssignProcessToJobObject.argtypes = [wt.HANDLE, wt.HANDLE]
        class Basic(ctypes.Structure):
            _fields_ = [("processTime", ctypes.c_longlong), ("jobTime", ctypes.c_longlong), ("flags", wt.DWORD),
                        ("minimum", ctypes.c_size_t), ("maximum", ctypes.c_size_t), ("active", wt.DWORD),
                        ("affinity", ctypes.c_size_t), ("priority", wt.DWORD), ("scheduling", wt.DWORD)]
        class Io(ctypes.Structure):
            _fields_ = [("counter" + str(i), ctypes.c_ulonglong) for i in range(6)]
        class Extended(ctypes.Structure):
            _fields_ = [("basic", Basic), ("io", Io), ("processMemory", ctypes.c_size_t),
                        ("jobMemory", ctypes.c_size_t), ("peakProcess", ctypes.c_size_t), ("peakJob", ctypes.c_size_t)]
        self.extended, self.job = Extended, None
        mask, system = ctypes.c_size_t(), ctypes.c_size_t()
        self.check(self.k.GetProcessAffinityMask(process._handle, ctypes.byref(mask), ctypes.byref(system)))
        self.report = dict(mode="unrestricted-desktop", original_affinity=mask.value)
        if enabled:
            allowed = [i for i in range(ctypes.sizeof(ctypes.c_size_t) * 8) if mask.value & (1 << i)]
            if len(allowed) < 2: raise RuntimeError("Two logical processors unavailable")
            target = sum(1 << i for i in allowed[:2]); self.check(self.k.SetProcessAffinityMask(process._handle, target))
            self.job = self.k.CreateJobObjectW(None, None)
            if not self.job: self.check(0)
            limits = Extended(); limits.basic.flags = 0x200 | 0x2000 | 0x8
            limits.basic.active = 1; limits.jobMemory = 2 * 1024 ** 3
            self.check(self.k.SetInformationJobObject(self.job, 9, ctypes.byref(limits), ctypes.sizeof(limits)))
            self.check(self.k.AssignProcessToJobObject(self.job, process._handle))
            actual = Extended(); self.check(self.k.QueryInformationJobObject(self.job, 9, ctypes.byref(actual), ctypes.sizeof(actual), None))
            mask2, system2 = ctypes.c_size_t(), ctypes.c_size_t()
            self.check(self.k.GetProcessAffinityMask(process._handle, ctypes.byref(mask2), ctypes.byref(system2)))
            if mask2.value != target or actual.jobMemory != 2 * 1024 ** 3: raise RuntimeError("Budget readback mismatch")
            self.report = dict(mode="windows-job-budget", logical_cpu_ids=allowed[:2], affinity_mask=mask2.value,
                               committed_memory_limit_bytes=actual.jobMemory, process_limit=actual.basic.active,
                               total_machine_physical_memory_is_2GiB=False)

    @staticmethod
    def check(value):
        if not value: raise ctypes.WinError(ctypes.get_last_error())

    def sample(self):
        class Memory(ctypes.Structure):
            _fields_ = [("cb", wt.DWORD), ("pageFaults", wt.DWORD)] + [(n, ctypes.c_size_t) for n in
                ("peakRss", "rss", "peakPaged", "paged", "peakNonPaged", "nonPaged", "pagefile", "peakPagefile")]
        info = Memory(); info.cb = ctypes.sizeof(info)
        psapi = ctypes.WinDLL("psapi", use_last_error=True)
        psapi.GetProcessMemoryInfo.argtypes = [wt.HANDLE, ctypes.c_void_p, wt.DWORD]
        self.check(psapi.GetProcessMemoryInfo(self.process._handle, ctypes.byref(info), ctypes.sizeof(info)))
        data = dict(rss_bytes=info.rss, peak_rss_bytes=info.peakRss)
        if self.job:
            limits = self.extended(); self.check(self.k.QueryInformationJobObject(self.job, 9, ctypes.byref(limits), ctypes.sizeof(limits), None))
            data["peak_job_committed_bytes"] = limits.peakJob
        return data

    def close(self):
        if self.job: self.k.CloseHandle(self.job); self.job = None


def main(o):
    root = pathlib.Path(__file__).resolve().parents[1]
    output = pathlib.Path(o.output).resolve(); output.mkdir(parents=True, exist_ok=True)
    processes, streams, monitor = [], [], None
    try:
        server_log = output / "server.log"; stream = server_log.open("w", encoding="utf-8"); streams.append(stream)
        temporary = output / "tmp"; temporary.mkdir(exist_ok=True)
        flags = ["-Djava.io.tmpdir=" + str(temporary), "-Xms128m", "-Xmx512m", "-XX:+UseG1GC", "-XX:MaxDirectMemorySize=256m", "-XX:MaxMetaspaceSize=192m"]
        if o.budget: flags.append("-XX:ActiveProcessorCount=2")
        server_args = ["tools-server", "--port=0", "--game=" + o.game, "--rooms=" + str(o.rooms), "--players=" + str(o.players),
                       "--seconds=" + str(o.seconds + 10), "--collision=" + str(o.collision).lower(), "--metrics=" + str(output / "server.json")]
        if o.jfr: server_args.append("--jfr=" + str(output / "server.jfr"))
        if o.replay: server_args.append("--replay-dir=" + str(output / "replays"))
        server = subprocess.Popen([o.java] + flags + ["-cp", o.classpath, "dev.gamecore.server.GameServerMain"] + server_args,
                                  cwd=root, stdout=stream, stderr=subprocess.STDOUT); processes.append(server)
        if os.name == "nt": monitor = WindowsBudget(server, o.budget)
        elif o.budget: raise RuntimeError("Use Linux Docker/cgroup recipe for OS budget")
        samples = []
        deadline, port = time.monotonic() + 35, None
        while time.monotonic() < deadline and server.poll() is None:
            match = re.search(r"TOOLS_SERVER_READY port=(\d+)", server_log.read_text(encoding="utf-8", errors="replace"))
            if match: port = int(match[1]); break
            time.sleep(.05)
        if port is None: raise RuntimeError("Server startup failed: " + server_log.read_text(errors="replace"))
        client_log = (output / "client.log").open("w", encoding="utf-8"); streams.append(client_log)
        client = subprocess.Popen([o.java, "-Xmx512m", "-cp", o.classpath, "dev.gamecore.server.GameServerMain", "load",
                                   "--port=" + str(port), "--game=" + o.game, "--rooms=" + str(o.rooms), "--players=" + str(o.players),
                                   "--clients=" + str(o.clients), "--seconds=" + str(o.seconds), "--hz=" + str(o.hz),
                                   "--collision=" + str(o.collision).lower(), "--output=" + str(output / "client.json")],
                                  cwd=root, stdout=client_log, stderr=subprocess.STDOUT); processes.append(client)
        start, deadline = time.monotonic(), time.monotonic() + o.seconds + 35
        while server.poll() is None and time.monotonic() < deadline:
            if monitor:
                row = monitor.sample(); row["seconds_after_client_launch"] = time.monotonic() - start; samples.append(row)
            time.sleep(.25)
        server.wait(timeout=5); client.wait(timeout=5)
        if server.returncode or client.returncode: raise RuntimeError("Benchmark process failure; inspect logs")
        system = dict(server_pid=server.pid, client_pid=client.pid, resource_constraint=monitor.report if monitor else dict(mode="unrestricted"), samples=samples)
        (output / "os-metrics.json").write_text(json.dumps(system, indent=2), encoding="utf-8")
        if o.jfr:
            subprocess.run([o.java, "-cp", o.classpath, "dev.gamecore.server.GameServerMain", "jfr-report", "--file=" + str(output / "server.jfr"),
                            "--output=" + str(output / "jfr-summary.json")], cwd=root, check=True)
        print("BENCHMARK_COMPLETE " + str(output), flush=True)
    finally:
        for process in reversed(processes):
            if process.poll() is None: process.terminate()
            try: process.wait(timeout=10)
            except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=5)
        if monitor: monitor.close()
        for stream in streams: stream.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--java", default="java"); parser.add_argument("--classpath", required=True)
    parser.add_argument("--output", required=True); parser.add_argument("--game", choices=("all", "tank", "snake", "fight"), default="all")
    parser.add_argument("--rooms", type=int, default=3); parser.add_argument("--players", type=int, default=2)
    parser.add_argument("--clients", type=int, default=6); parser.add_argument("--seconds", type=int, default=10)
    parser.add_argument("--hz", type=int, default=20); parser.add_argument("--collision", action=argparse.BooleanOptionalAction, default=True)
    parser.add_argument("--budget", action="store_true"); parser.add_argument("--jfr", action="store_true"); parser.add_argument("--replay", action="store_true")
    main(parser.parse_args())
