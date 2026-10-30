"""Run the supplied mock on loopback without modifying the exercise server."""
import http.server
from pathlib import Path
import runpy


class LoopbackHTTPServer(http.server.HTTPServer):
    def __init__(self, server_address, handler, bind_and_activate=True):
        # Only the Java app should be exposed to the hosting platform.
        super().__init__(("127.0.0.1", server_address[1]), handler, bind_and_activate)


if __name__ == "__main__":
    http.server.HTTPServer = LoopbackHTTPServer
    runpy.run_path(
        str(Path(__file__).resolve().parents[1] / "starter-kit/mock-apis/mock_server.py"),
        run_name="__main__",
    )
