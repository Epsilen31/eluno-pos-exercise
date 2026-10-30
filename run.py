#!/usr/bin/env python3
"""One-command local launcher. Application logic is Java; this only manages processes."""
import argparse
from contextlib import ExitStack
import datetime
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent
APP_ROOT = ROOT / "billing-service"


def wait_for(url, process, timeout=60):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process is not None and process.poll() is not None:
            raise RuntimeError("Process stopped during startup; inspect session log files")
        try:
            with urllib.request.urlopen(url, timeout=1) as response:
                if response.status == 200:
                    return
        except (OSError, ValueError):
            time.sleep(0.2)
    raise RuntimeError("Timed out waiting for " + url)


def free_port(port):
    with socket.socket() as sock:
        try:
            sock.bind(("127.0.0.1", port))
        except OSError:
            raise RuntimeError("Port %s is busy; stop the existing process or choose a different port" % port)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--mock-port", type=int, default=8787)
    parser.add_argument("--no-flake", action="store_true")
    parser.add_argument("--no-build", action="store_true")
    parser.add_argument("--startup-timeout", type=int, default=60, help="Seconds to allow each service to start")
    parser.add_argument("--console-logs", action="store_true", help="Send child logs to the hosting console")
    parser.add_argument("--external-mock", action="store_true", help="Keep an independently running mock; permits app restarts")
    parser.add_argument("--database", help="Existing/new H2 file path; only with --external-mock")
    args = parser.parse_args()
    if args.startup_timeout < 1:
        parser.error("--startup-timeout must be positive")
    if args.database and not args.external_mock:
        parser.error("--database requires --external-mock: never reuse a ledger against a freshly reset mock")
    os.chdir(ROOT)
    if not shutil.which("java"):
        raise RuntimeError("Install JDK 21+ and put java on PATH")
    free_port(args.port)
    if not args.external_mock:
        free_port(args.mock_port)
    if not args.no_build:
        mvn = shutil.which("mvn") or str(APP_ROOT / ("mvnw.cmd" if os.name == "nt" else "mvnw"))
        command = [mvn, "-q", "-DskipTests", "package"]
        if os.name != "nt" and not shutil.which("mvn"):
            command = ["sh"] + command
        subprocess.run(command, check=True, cwd=APP_ROOT)
    jar = APP_ROOT / "target/till-1.0.0.jar"
    if not jar.exists():
        raise RuntimeError("Build missing; run without --no-build")
    session_id = datetime.datetime.now().strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:6]
    session = ROOT / ".local" / session_id
    session.mkdir(parents=True)
    database = Path(args.database).resolve() if args.database else session / "till"
    database.parent.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ)
    env.update(POS_DATA=str(ROOT / "starter-kit/data"), PORT=str(args.port), POS_UPSTREAM="http://localhost:%s" % args.mock_port,
               POS_DATABASE="jdbc:h2:file:%s;DB_CLOSE_ON_EXIT=FALSE" % database.as_posix())
    children = []
    try:
        with ExitStack() as stack:
            mock_log = None if args.console_logs else stack.enter_context((session / "mock.log").open("w", encoding="utf-8"))
            app_log = None if args.console_logs else stack.enter_context((session / "app.log").open("w", encoding="utf-8"))
            mock = None
            if not args.external_mock:
                command = [sys.executable, "-u", "scripts/start_mock.py", "--port", str(args.mock_port)]
                if args.no_flake:
                    command.append("--no-flake")
                mock = subprocess.Popen(command, stdout=mock_log, stderr=subprocess.STDOUT)
                children.append(mock)
            wait_for(env["POS_UPSTREAM"] + "/health", mock, timeout=args.startup_timeout)
            print("Starting Eluno POS on port %s (startup timeout: %ss)" % (args.port, args.startup_timeout), flush=True)
            app = subprocess.Popen(["java", "-jar", str(jar)], env=env, stdout=app_log, stderr=subprocess.STDOUT)
            children.append(app)
            wait_for("http://localhost:%s/api/health" % args.port, app, timeout=args.startup_timeout)
            info = dict(api="http://localhost:%s" % args.port, database=str(database), logs=str(session))
            (ROOT / ".local/current-session.json").write_text(json.dumps(info, indent=2), encoding="utf-8")
            print("\nEluno POS ready: " + info["api"], flush=True)
            print("Run in a second terminal: python scripts/demo.py", flush=True)
            print("Database: " + str(database), flush=True)
            print("Logs: " + str(session), flush=True)
            print("Ctrl+C stops this app and its managed mock. Each managed mock gets a fresh ledger; old ledgers are retained.", flush=True)
            app.wait()
            if app.returncode:
                raise RuntimeError("Application stopped unexpectedly; inspect " + str(session / "app.log"))
    except KeyboardInterrupt:
        print("\nStopping local session...", flush=True)
    finally:
        for child in reversed(children):
            if child.poll() is None:
                child.terminate()
                try:
                    child.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    child.kill()
                    child.wait(timeout=5)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as exc:
        print("Error: " + str(exc), file=sys.stderr)
        sys.exit(1)
