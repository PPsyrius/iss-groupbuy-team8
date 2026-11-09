"""Exercise the workflow download with real curl and a disposable loopback TLS server."""

import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
import shutil
import ssl
import subprocess
import tempfile
import threading
import unittest

from dast_baseline_test import BETA_ADDON, BETA_SHA256, BETA_URL, bash_path, download_script


PAYLOAD = b"local add-on fixture for retry and checksum checks"


class DastAddonHttpTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.bash = bash_path()
        openssl = (str(Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git/usr/bin/openssl.exe")
                   if os.name == "nt" else shutil.which("openssl"))
        if not cls.bash or not openssl:
            raise RuntimeError("Bash, curl and OpenSSL are required for the download regressions")
        cls.certs = tempfile.TemporaryDirectory(prefix="dast-tls-", dir=os.environ.get("DAST_TEST_TMP"))
        cls.addClassCleanup(cls.certs.cleanup)
        cls.certificate = Path(cls.certs.name) / "certificate.pem"
        key = Path(cls.certs.name) / "key.pem"
        # Generate a test-only key; no key or trusted CA material is committed.
        subprocess.run([openssl, "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-sha256", "-days", "1",
            "-subj", "/CN=localhost", "-addext", "subjectAltName=IP:127.0.0.1",
            "-keyout", str(key), "-out", str(cls.certificate)], check=True, capture_output=True, timeout=15)
        cls.tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        cls.tls.load_cert_chain(cls.certificate, key)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="dast-http-", dir=os.environ.get("DAST_TEST_TMP"))
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name)
        self.requests = []
        self.failures = 1
        self.truncate_first = False
        self.corrupt = False
        test = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                test.requests.append(self.path)
                attempt = len(test.requests)
                if attempt <= test.failures:
                    status, body = 503, b"temporary download failure"
                else:
                    status, body = 200, b"corrupt add-on" if test.corrupt else PAYLOAD
                self.send_response(status)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                if test.truncate_first and attempt == 1:
                    self.wfile.write(body[:5])
                    self.wfile.flush()
                    self.close_connection = True
                else:
                    self.wfile.write(body)

            def log_message(self, *args):
                pass

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.server.socket = self.tls.wrap_socket(self.server.socket, server_side=True)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self.stop_server)

    def stop_server(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def run_download(self):
        url = f"https://127.0.0.1:{self.server.server_port}/{BETA_ADDON}"
        # Only the endpoint, fixture digest and local CA differ; all retry/protocol/time bounds stay intact.
        script = download_script().replace(BETA_URL, url).replace(BETA_SHA256, hashlib.sha256(PAYLOAD).hexdigest())
        script = script.replace("curl --fail", 'curl --cacert "$DAST_TEST_CA" --noproxy "*" --fail')
        completed = subprocess.run([self.bash, "--noprofile", "--norc", "-s"],
            input=script + '\nprintf ready > "$RUNNER_TEMP/ready"\n', text=True, capture_output=True,
            timeout=20, env={**os.environ, "RUNNER_TEMP": self.output.as_posix(),
                             "DAST_TEST_CA": self.certificate.as_posix()})
        self.assertEqual(set(self.requests), {"/" + BETA_ADDON}, completed.stdout + completed.stderr)
        return completed

    def test_real_curl_retries_a_503_and_accepts_the_verified_download(self):
        completed = self.run_download()
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        self.assertEqual(len(self.requests), 2)
        self.assertEqual((self.output / "zap-addons" / BETA_ADDON).read_bytes(), PAYLOAD)
        self.assertTrue((self.output / "ready").is_file())

    def test_real_curl_discards_a_truncated_transfer_before_retrying(self):
        self.failures = 0
        self.truncate_first = True
        completed = self.run_download()
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        self.assertEqual(len(self.requests), 2)
        self.assertEqual((self.output / "zap-addons" / BETA_ADDON).read_bytes(), PAYLOAD)

    def test_real_curl_stops_after_the_configured_retries_are_exhausted(self):
        self.failures = 100
        completed = self.run_download()
        self.assertEqual(completed.returncode, 22, completed.stdout + completed.stderr)
        self.assertEqual(len(self.requests), 4)
        self.assertFalse((self.output / "ready").exists())

    def test_retry_success_cannot_bypass_checksum_validation(self):
        self.corrupt = True
        completed = self.run_download()
        self.assertNotEqual(completed.returncode, 0)
        self.assertEqual(len(self.requests), 2)
        self.assertIn("FAILED", completed.stdout + completed.stderr)
        self.assertFalse((self.output / "ready").exists())


if __name__ == "__main__":
    unittest.main()
