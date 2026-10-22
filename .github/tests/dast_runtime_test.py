"""Opt-in ZAP 2.17 smoke tests against a disposable loopback app, never the deployed site."""

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import threading
import unittest
from urllib.parse import parse_qs, urlsplit

from dast_traffic_test import HTML, PRODUCT, module, page


@unittest.skipUnless(os.environ.get("DAST_ZAP_HOME"), "Set DAST_ZAP_HOME to a local ZAP 2.17.0 bundle")
class DastRuntimeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="dast-runtime-", dir=os.environ.get("DAST_TEST_TMP"))
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.output = self.directory / "reports"
        self.bad_detail = False
        self.empty_catalog = False
        self.disable_rules = False
        self.received = []
        test = self
        markup = HTML.replace("</body>", '<a href="/api/v1/auth/oauth2/google">Sign in</a>'
                              '<form method="post" action="/api/v1/payments/intents"><input name="x"></form></body>')

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                test.received.append(("GET", self.path))
                parsed = urlsplit(self.path)
                query = parse_qs(parsed.query)
                status, content_type = 200, "application/json"
                if parsed.path in ("/", "/products/" + PRODUCT["id"]):
                    content_type, body = "text/html", markup
                elif parsed.path == "/assets/app.js":
                    content_type, body = "text/javascript", "export {};"
                elif parsed.path == "/api/v1/products":
                    number = int(query.get("page", ["1"])[0])
                    size = int(query.get("size", ["20"])[0])
                    if number < 1:
                        status, body = 400, {"code": "COMMON_400_002"}
                    else:
                        body = page(items=[PRODUCT] if number == 1 and not test.empty_catalog else [], page_number=number, size=size)
                elif parsed.path == "/api/v1/products/" + PRODUCT["id"]:
                    if test.bad_detail:
                        content_type, body = "text/html", markup
                    else:
                        body = {"code": module.SUCCESS, "data": PRODUCT}
                elif parsed.path in ("/api/v1/orders/me", "/api/v1/admin/products"):
                    status, body = 401, {"code": "COMMON_401_001"}
                else:
                    status, body = 404, {"code": "COMMON_404_001"}
                payload = (body if isinstance(body, str) else json.dumps(body)).encode("utf-8")
                self.send_response(status)
                self.send_header("Content-Type", content_type + "; charset=utf-8")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

            def do_POST(self):
                test.received.append(("POST", self.path))
                self.send_response(405)
                self.end_headers()

            def log_message(self, *args):
                pass

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self.stop_server)

    def stop_server(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def run_zap(self):
        module.preflight(self.output, "http://127.0.0.1:" + str(self.server.server_port))
        plan_path = self.output / "zap-plan.yaml"
        plan = json.loads(plan_path.read_text(encoding="utf-8"))
        if self.disable_rules:
            plan["jobs"][0]["parameters"]["disableAllRules"] = True
            plan["jobs"][0]["rules"] = []
        for job in plan["jobs"]:
            if job["type"] == "report":
                job["parameters"]["reportDir"] = str(self.output)
            elif job["type"] == "export":
                job["parameters"]["fileName"] = str(self.output / "traffic.har")
            elif job["type"] == "script" and "inline" in job["parameters"]:
                job["parameters"]["inline"] = job["parameters"]["inline"].replace("/zap/wrk/", self.output.as_posix() + "/")
        module.write_json(plan_path, plan)
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        home = self.directory / "zap-state"
        home.mkdir()
        zap = Path(os.environ["DAST_ZAP_HOME"]).expanduser().resolve()
        command = ["java", "-Xmx512m", "-Djava.awt.headless=true", "-jar", str(zap / "zap-2.17.0.jar"),
                   "-cmd", "-dir", str(home), "-host", "127.0.0.1", "-port", str(port),
                   "-addoninstall", "pscanrulesBeta", "-autorun", str(plan_path)]
        log_path = self.directory / "zap-console.log"
        with log_path.open("w", encoding="utf-8") as log:
            completed = subprocess.run(command, cwd=zap, stdout=log, stderr=subprocess.STDOUT, timeout=180)
        self.assertEqual(completed.returncode, 0, log_path.read_text(encoding="utf-8")[-10000:])
        result = module.verify(self.output, "success")
        self.assertTrue(all(method == "GET" and not path.startswith("/api/v1/auth/")
                            for method, path in self.received), self.received)
        self.assertEqual(sum(path == "/api/v1/orders/me" for _, path in self.received), 1, self.received)
        self.assertEqual(sum(path == "/api/v1/admin/products" for _, path in self.received), 1, self.received)
        self.assertFalse((self.output / "traffic.har").exists())
        return result

    def test_native_plan_records_all_ten_gets_without_following_oauth_or_submitting_forms(self):
        result = self.run_zap()
        self.assertTrue(result["complete"], result)
        self.assertEqual(result["covered"], 10)
        self.assertTrue(result["passive_scan"]["verified"])
        self.assertGreaterEqual(result["passive_scan"]["executed_rules"], len(module.REQUIRED_PASSIVE_RULES))

    def test_http_200_html_api_fallback_is_incomplete_even_when_zap_succeeds(self):
        self.bad_detail = True
        result = self.run_zap()
        self.assertFalse(result["complete"])
        self.assertEqual(result["covered"], 9)
        self.assertFalse(next(check for check in result["checks"] if check["name"] == "Product detail")["passed"])

    def test_empty_catalog_still_runs_zap_and_reports_eight_of_ten_with_passive_evidence(self):
        self.empty_catalog = True
        result = self.run_zap()
        self.assertFalse(result["complete"], result)
        self.assertEqual((result["covered"], result["expected"]), (8, 10))
        self.assertEqual(result["unavailable"], ["Product detail", "SPA product deep link"])
        self.assertTrue(result["passive_scan"]["verified"])
        self.assertFalse(any("existingId" in path for _, path in self.received))

    def test_disabled_passive_rules_are_rejected_even_when_zap_and_all_ten_requests_succeed(self):
        self.disable_rules = True
        result = self.run_zap()
        self.assertEqual(result["covered"], 10)
        self.assertFalse(result["complete"], result)
        self.assertTrue(any("missing or disabled" in error for error in result["errors"]), result)


if __name__ == "__main__":
    unittest.main()
