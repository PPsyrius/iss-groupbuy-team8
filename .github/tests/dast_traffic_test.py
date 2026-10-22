import base64
import importlib.util
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import re
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("dast_baseline", ROOT / ".github/scripts/dast-baseline.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

BASE = "https://example.test"
PRODUCT = {"id": "01990000-0000-7000-8000-000000000001", "title": "Rice & beans", "category": "food & drinks"}
HTML = '<html><body><div id="root"></div><script type="module" src="/assets/app.js"></script></body></html>'


def page(items=None, page_number=1, size=20):
    return {"code": module.SUCCESS, "data": {"items": [PRODUCT] if items is None else items,
            "total": 1, "page": page_number, "size": size}}


def preflight_response(url):
    if url == BASE + "/":
        return 200, "text/html; charset=utf-8", HTML
    if url == BASE + "/api/v1/products":
        return 200, "application/json", json.dumps(page())
    raise AssertionError("Unexpected preflight request: " + url)


def recorded_response(probe):
    if probe["kind"] == "html":
        content_type, body = "text/html", HTML
    else:
        content_type = "application/json"
        if probe["kind"] == "page":
            payload = page(page_number=probe["page"], size=probe.get("size", 20))
        elif probe["kind"] == "detail":
            payload = {"code": module.SUCCESS, "data": {"id": PRODUCT["id"]}}
        else:
            payload = {"code": probe["code"], "message": "Expected refusal"}
        body = json.dumps(payload)
    return {"request": {"method": probe["method"], "url": probe["url"]},
            "response": {"status": probe["status"], "headers": [{"name": "Content-Type", "value": content_type}],
                         "content": {"mimeType": content_type, "text": body}}}


def passive_evidence(output):
    for phase in ("start", "finish"):
        module.write_json(output / f"passive-{phase}.json", {"schema": 1, "queued": 0, "scanner_errors": 0,
            "request_bodies_skipped": 0, "response_bodies_skipped": 0,
            "rules": [{"id": rule, "enabled": True, "threshold": "MEDIUM",
                       "time_ms": None if phase == "start" else 0} for rule in module.REQUIRED_PASSIVE_RULES]})


class DastTrafficTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name) / "scan"

    def prepare(self, get=preflight_response):
        module.preflight(self.output, BASE, get=get)
        self.manifest = json.loads((self.output / "requests.json").read_text(encoding="utf-8"))
        self.entries = [recorded_response(probe) for probe in self.manifest["requests"] if not probe.get("unavailable")]
        for extension in ("html", "md"):
            (self.output / f"report.{extension}").write_text("ZAP report", encoding="utf-8")
        module.write_json(self.output / "report.json", {"site": [{"alerts": [{"riskcode": "2"}, {"riskcode": "1"}]}]})
        passive_evidence(self.output)

    def verify(self, outcome="success"):
        module.write_json(self.output / "traffic.har", {"log": {"entries": self.entries}})
        return module.verify(self.output, outcome)

    def test_origin_normalizes_default_ports_and_rejects_unsafe_targets(self):
        self.assertEqual(module.origin("https://EXAMPLE.test:443/"), BASE)
        self.assertEqual(module.origin("http://[::1]:8080"), "http://[::1]:8080")
        for value in ("", "file:///etc/passwd", "https://user:secret@example.test", BASE + "/api",
                      BASE + "?token=secret", BASE + "#fragment", "https://exam\nple.test", BASE + ":0"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                module.origin(value)

    def test_preflight_validates_two_responses_before_generating_ten_get_probes(self):
        calls = []
        def get(url):
            calls.append(url)
            return preflight_response(url)
        module.preflight(self.output, BASE, get=get)
        manifest = json.loads((self.output / "requests.json").read_text())
        self.assertEqual(calls, [BASE + "/", BASE + "/api/v1/products"])
        self.assertEqual(len(manifest["requests"]), 10)
        self.assertEqual({item["method"] for item in manifest["requests"]}, {"GET"})
        self.assertIn("category=food+%26+drinks", manifest["requests"][3]["url"])
        self.assertIn("keyword=Rice&ai=false", manifest["requests"][4]["url"])
        self.assertIn(PRODUCT["id"], manifest["requests"][5]["url"])
        self.assertFalse(json.loads((self.output / "coverage.json").read_text())["complete"])

    def test_preflight_rejects_wrong_status_content_type_or_spa_fallback(self):
        for status, content_type, body in ((503, "text/html", HTML), (200, "text/plain", HTML),
                                          (200, "text/html", "<html>Login required</html>")):
            with self.subTest(status=status, content_type=content_type):
                output = Path(self.temp.name) / f"bad-root-{status}-{len(body)}-{len(content_type)}"
                with self.assertRaises(module.ScanError):
                    module.preflight(output, BASE, get=lambda url: (status, content_type, body))
                self.assertFalse((output / "zap-plan.yaml").exists())
        with self.assertRaises(module.ScanError):
            module.preflight(self.output, BASE, get=lambda url: preflight_response(url) if url.endswith("/")
                             else (200, "text/html", HTML))

    def test_preflight_rejects_invalid_nonempty_fixture(self):
        for index, items in enumerate(([{"id": "not-a-uuid", "title": "Rice"}],
                                       [{"id": PRODUCT["id"], "title": ""}])):
            with self.subTest(items=items), self.assertRaises(module.ScanError):
                module.preflight(Path(self.temp.name) / str(index), BASE,
                    get=lambda url: preflight_response(url) if url.endswith("/")
                    else (200, "application/json", json.dumps(page(items))))

    def test_empty_catalog_still_scans_eight_probes_but_cannot_claim_complete_coverage(self):
        self.prepare(get=lambda url: preflight_response(url) if url.endswith("/")
                     else (200, "application/json", json.dumps(page([]))))
        plan = json.loads((self.output / "zap-plan.yaml").read_text())
        requests = next(job["requests"] for job in plan["jobs"] if job["type"] == "requestor")
        self.assertEqual(len(requests), 8)
        self.assertFalse(any("{existingId}" in request["url"] for request in requests))
        for name in ("SPA root", "Catalog", "Category filter", "Keyword search", "Anonymous orders denial"):
            self.assertIn(name, [request["name"] for request in requests])
        result = self.verify()
        self.assertFalse(result["complete"])
        self.assertEqual((result["covered"], result["expected"]), (8, 10))
        self.assertEqual(result["unavailable"], ["Product detail", "SPA product deep link"])
        self.assertTrue(result["passive_scan"]["verified"])
        self.assertIn("UNAVAILABLE", module.markdown(result))
        self.assertIn("Catalog is empty", module.markdown(result))

    def test_preflight_rejects_stale_output_without_overwriting_it(self):
        self.prepare()
        manifest = (self.output / "requests.json").read_bytes()
        with self.assertRaisesRegex(module.ScanError, "empty output"):
            module.preflight(self.output, BASE, get=preflight_response)
        self.assertEqual((self.output / "requests.json").read_bytes(), manifest)

    def test_uncategorized_product_still_gets_an_explicit_filter_probe(self):
        catalog = page([{**PRODUCT, "category": None}])
        probes = module.required_requests(BASE, catalog)
        self.assertIn("category=dast-unlisted-category", probes[3]["url"])

    def test_wrong_page_size_detail_or_missing_error_code_is_not_successful_coverage(self):
        for index, body in ((2, page(page_number=1, size=20)),
                            (5, {"code": module.SUCCESS, "data": {"id": "another-product"}}),
                            (8, {"message": "Login required"})):
            with self.subTest(index=index):
                self.prepare_fresh()
                self.entries[index]["response"]["content"]["text"] = json.dumps(body)
                self.assertFalse(self.verify()["complete"])

    def test_plan_has_one_scoped_passive_crawl_and_no_mutating_or_active_jobs(self):
        self.prepare()
        plan = json.loads((self.output / "zap-plan.yaml").read_text())
        types = [job["type"] for job in plan["jobs"]]
        self.assertEqual(types.count("spider"), 1)
        self.assertNotIn("activeScan", types)
        self.assertNotIn("openapi", types)
        spider = next(job for job in plan["jobs"] if job["type"] == "spider")
        self.assertEqual(spider["parameters"]["maxDuration"], 3)
        self.assertFalse(spider["parameters"]["postForm"])
        self.assertFalse(spider["parameters"]["processForm"])
        self.assertEqual(spider["parameters"]["context"], "public-crawl")
        excluded = re.compile(plan["env"]["contexts"][1]["excludePaths"][0])
        for path in ("/", "/assets/app.js", "/products/" + PRODUCT["id"]):
            self.assertIsNone(excluded.match(BASE + path))
        for url in (BASE + "/api/v1/products", BASE + "/api/v1/auth/oauth2/google",
                    BASE + "/api/v1/admin/products", "https://elsewhere.test/"):
            self.assertIsNotNone(excluded.match(url))
        export = next(job for job in plan["jobs"] if job["type"] == "export")
        self.assertEqual(export["parameters"]["source"], "all")
        self.assertTrue(export["alwaysRun"])
        self.assertEqual([job["parameters"]["template"] for job in plan["jobs"] if job["type"] == "report"],
                         ["traditional-html", "traditional-json", "traditional-md"])
        config = plan["jobs"][0]
        self.assertEqual(config["parameters"]["maxAlertsPerRule"], 0)
        self.assertFalse(config["parameters"]["disableAllRules"])
        self.assertEqual({rule["id"] for rule in config["rules"]}, set(module.REQUIRED_PASSIVE_RULES))
        self.assertLess(types.index("script"), types.index("requestor"))
        self.assertEqual(types[types.index("passiveScan-wait") + 1], "script")

    def test_complete_coverage_uses_actual_zap_traffic_and_retains_advisory_alerts(self):
        self.prepare()
        result = self.verify()
        self.assertTrue(result["complete"], result)
        self.assertEqual((result["covered"], result["expected"]), (10, 10))
        self.assertEqual(result["alerts"], {"2": 1, "1": 1})
        self.assertEqual(len(result["visited"]), 10)
        self.assertFalse((self.output / "traffic.har").exists())
        self.assertNotIn("response", result["visited"][0])
        self.assertIn("COMPLETE", module.markdown(result))
        self.assertTrue(result["passive_scan"]["verified"])
        self.assertIn("Passive processing verified", module.markdown(result))

    def test_zero_alerts_can_pass_with_passive_execution_evidence(self):
        self.prepare()
        module.write_json(self.output / "report.json", {"site": [{"alerts": []}]})
        self.assertTrue(self.verify()["complete"])

    def test_missing_disabled_or_unexecuted_rules_fail_even_with_ten_valid_responses(self):
        changes = [lambda data: data["rules"].clear(),
                   lambda data: data["rules"][0].update(enabled=False),
                   lambda data: data["rules"][0].update(threshold="OFF"),
                   lambda data: data["rules"][0].update(time_ms=None),
                   lambda data: data["rules"][0].pop("time_ms"),
                   lambda data: data.update(queued=1),
                   lambda data: data.update(scanner_errors=1),
                   lambda data: data.update(response_bodies_skipped=1)]
        for change in changes:
            with self.subTest(change=change):
                self.prepare_fresh()
                path = self.output / "passive-finish.json"
                evidence = json.loads(path.read_text())
                change(evidence)
                module.write_json(path, evidence)
                result = self.verify()
                self.assertEqual(result["covered"], 10)
                self.assertFalse(result["complete"])
                self.assertTrue(result["errors"])
                self.assertIn("Passive processing NOT VERIFIED", module.markdown(result))

    def test_missing_or_stale_passive_snapshots_fail_closed(self):
        for phase in ("start", "finish"):
            with self.subTest(phase=phase):
                self.prepare_fresh()
                (self.output / f"passive-{phase}.json").unlink()
                self.assertFalse(self.verify()["complete"])
        self.prepare_fresh()
        path = self.output / "passive-start.json"
        evidence = json.loads(path.read_text())
        evidence["rules"][0]["time_ms"] = 0
        module.write_json(path, evidence)
        self.assertFalse(self.verify()["complete"])

    def test_missing_required_rule_and_unexecuted_extra_enabled_rule_fail(self):
        self.prepare()
        path = self.output / "passive-start.json"
        evidence = json.loads(path.read_text())
        evidence["rules"][0]["enabled"] = False
        module.write_json(path, evidence)
        self.assertFalse(self.verify()["complete"])
        passive_evidence(self.output)
        for phase in ("start", "finish"):
            path = self.output / f"passive-{phase}.json"
            evidence = json.loads(path.read_text())
            evidence["rules"].append({"id": 12345, "enabled": True, "threshold": "MEDIUM", "time_ms": None})
            module.write_json(path, evidence)
        self.assertFalse(self.verify()["complete"])

    def test_static_assets_cannot_substitute_for_missing_application_requests(self):
        self.prepare()
        self.entries = [{"request": {"method": "GET", "url": BASE + f"/assets/{i}.js"},
                         "response": {"status": 200, "headers": [{"name": "Content-Type", "value": "text/javascript"}],
                                      "content": {"text": "export {}"}}} for i in range(100)]
        result = self.verify()
        self.assertFalse(result["complete"])
        self.assertEqual(result["covered"], 0)
        self.assertEqual(len(result["visited"]), 100)
        self.assertIn("INCOMPLETE", module.markdown(result))
        self.assertIn("not visited", module.markdown(result))

    def test_bad_status_json_envelope_content_type_or_missing_body_fails_coverage(self):
        changes = [lambda entry: entry["response"].update(status=503),
                   lambda entry: entry["response"]["content"].update(text="not json"),
                   lambda entry: entry["response"]["content"].update(text='{"code":"WRONG"}'),
                   lambda entry: entry["response"].update(headers=[{"name": "Content-Type", "value": "text/html"}]),
                   lambda entry: entry["response"]["content"].pop("text")]
        for change in changes:
            with self.subTest(change=change):
                self.prepare_fresh()
                change(self.entries[1])
                result = self.verify()
                self.assertFalse(result["complete"])
                self.assertFalse(result["checks"][1]["passed"])

    def prepare_fresh(self):
        self.output = Path(self.temp.name) / f"scan-{len(list(Path(self.temp.name).iterdir()))}"
        self.prepare()

    def test_one_good_response_cannot_hide_a_second_failing_match(self):
        self.prepare()
        duplicate = recorded_response(self.manifest["requests"][1])
        duplicate["response"]["status"] = 500
        self.entries.append(duplicate)
        result = self.verify()
        self.assertFalse(result["complete"])
        self.assertEqual(result["checks"][1]["observed_statuses"], [200, 500])

    def test_zap_unfetched_parent_nodes_are_not_http_coverage(self):
        self.prepare()
        placeholder = recorded_response(self.manifest["requests"][1])
        placeholder["response"] = {"status": 0, "headers": [], "content": {}}
        self.entries.append(placeholder)
        result = self.verify()
        self.assertTrue(result["complete"], result)
        self.assertEqual(result["unfetched_nodes"], 1)
        self.assertEqual(len(result["visited"]), 10)
        self.entries = [placeholder]
        result = self.verify()
        self.assertFalse(result["complete"])
        self.assertEqual(result["covered"], 0)

    def test_encoded_query_order_and_base64_response_are_supported(self):
        self.prepare()
        self.entries[4]["request"]["url"] = BASE + "/api/v1/products?ai=false&keyword=Rice"
        content = self.entries[4]["response"]["content"]
        content["text"] = base64.b64encode(content["text"].encode()).decode()
        content["encoding"] = "base64"
        self.assertTrue(self.verify()["complete"])

    def test_scanner_failure_missing_har_and_missing_reports_are_incomplete(self):
        self.prepare()
        self.assertFalse(self.verify("failure")["complete"])
        self.assertFalse(module.verify(self.output, "success")["complete"])
        for extension in ("html", "json", "md"):
            with self.subTest(extension=extension):
                self.prepare_fresh()
                (self.output / f"report.{extension}").unlink()
                self.assertFalse(self.verify()["complete"])

    def test_oversized_or_malformed_har_and_malformed_report_are_incomplete(self):
        self.prepare()
        with patch.object(module, "MAX_HAR_BYTES", 1):
            self.assertFalse(self.verify()["complete"])
        (self.output / "traffic.har").write_text("invalid json", encoding="utf-8")
        self.assertFalse(module.verify(self.output, "success")["complete"])
        module.write_json(self.output / "report.json", [])
        self.assertFalse(self.verify()["complete"])

    def test_coverage_inventory_does_not_publish_raw_bodies_or_cookie_headers(self):
        self.prepare()
        self.entries[0]["response"]["headers"].append({"name": "Set-Cookie", "value": "sensitive-cookie-sentinel"})
        self.entries[0]["response"]["content"]["text"] += "<!-- private-body-sentinel -->"
        result = self.verify()
        self.assertTrue(result["complete"])
        text = (self.output / "coverage.json").read_text()
        self.assertNotIn("sensitive-cookie-sentinel", text)
        self.assertNotIn("private-body-sentinel", text)
        self.assertFalse((self.output / "traffic.har").exists())

    def test_mutating_or_cross_origin_traffic_fails_scope_validation(self):
        for method, url in (("POST", BASE + "/api/v1/products"), ("GET", "https://elsewhere.test/")):
            with self.subTest(method=method, url=url):
                self.prepare_fresh()
                self.entries[0]["request"].update(method=method, url=url)
                result = self.verify()
                self.assertFalse(result["complete"])
                self.assertIn("GET-only scope", result["errors"][0])

    def test_denials_cannot_be_200_wrapped_or_expose_success_data(self):
        self.prepare()
        self.entries[-1]["response"]["content"]["text"] = json.dumps({"code": "COMMON_401_001", "data": PRODUCT})
        self.assertFalse(self.verify()["complete"])

    def test_cli_generates_a_summary_for_preflight_failure(self):
        with patch.dict("os.environ", {"BASE_URL": "file:///no-network"}), patch("sys.stderr"):
            self.assertEqual(module.main(["preflight", "--output", str(self.output)]), 1)
        summary = Path(self.temp.name) / "summary.md"
        with patch("sys.stdout"):
            self.assertEqual(module.main(["verify", "--output", str(self.output), "--summary", str(summary)]), 1)
        self.assertIn("INCOMPLETE", summary.read_text())
        self.assertIn("BASE_URL", summary.read_text())

    def test_cli_success_writes_coverage_markdown_and_step_summary(self):
        self.prepare()
        module.write_json(self.output / "traffic.har", {"log": {"entries": self.entries}})
        summary = Path(self.temp.name) / "summary.md"
        with patch("sys.stdout"):
            self.assertEqual(module.main(["verify", "--output", str(self.output), "--scan-outcome", "success",
                                          "--summary", str(summary)]), 0)
        self.assertEqual(summary.read_bytes(), (self.output / "coverage.md").read_bytes())

    def test_cli_without_any_preflight_output_is_incomplete(self):
        with patch("sys.stdout"):
            self.assertEqual(module.main(["verify", "--output", str(self.output)]), 1)
        self.assertIn("INCOMPLETE", (self.output / "coverage.md").read_text())


class DastPreflightHttpTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.paths = []
        cls.slow_root = False
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                cls.paths.append(self.path)
                if self.path == "/redirect":
                    self.send_response(302)
                    self.send_header("Location", "/must-not-follow")
                    self.end_headers()
                    return
                if self.path == "/" and cls.slow_root:
                    self.send_response(200)
                    self.send_header("Content-Length", "1000")
                    self.end_headers()
                    try:
                        for _ in range(1000):
                            self.wfile.write(b"x")
                            self.wfile.flush()
                            time.sleep(0.02)
                    except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                        pass
                    return
                if self.path == "/large":
                    body = b"x" * (module.MAX_RESPONSE_BYTES + 1)
                elif self.path == "/":
                    body = HTML.encode()
                else:
                    body = json.dumps(page()).encode()
                self.send_response(200)
                self.send_header("Content-Type", "text/html" if self.path == "/" else "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *args):
                pass
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.base = "http://127.0.0.1:" + str(cls.server.server_port)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def test_http_preflight_uses_real_gets_and_emits_the_plan(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "reports"
            module.preflight(output, self.base)
            self.assertTrue((output / "zap-plan.yaml").is_file())
            observations = json.loads((output / "preflight.json").read_text())["requests"]
            self.assertEqual([item["status"] for item in observations], [200, 200])

    def test_http_preflight_does_not_follow_redirects(self):
        start = len(self.paths)
        status, _, _ = module.fetch(self.base + "/redirect")
        self.assertEqual(status, 302)
        self.assertEqual(self.paths[start:], ["/redirect"])

    def test_http_preflight_bounds_response_size(self):
        with self.assertRaisesRegex(module.ScanError, "two-MiB"):
            module.fetch(self.base + "/large")

    def test_workflow_wall_clock_timeout_stops_a_slowly_streaming_preflight(self):
        timeout = (str(Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git/usr/bin/timeout.exe")
                   if os.name == "nt" else shutil.which("timeout"))
        if not timeout or not Path(timeout).is_file():
            self.skipTest("GNU timeout is needed to test the workflow's wall-clock deadline")
        start = len(self.paths)
        started = time.monotonic()
        type(self).slow_root = True
        try:
            with tempfile.TemporaryDirectory() as directory:
                output = Path(directory) / "reports"
                completed = subprocess.run([timeout, "--signal=TERM", "--kill-after=0.5s", "2s",
                    sys.executable, "-B", (ROOT / ".github/scripts/dast-baseline.py").as_posix(),
                    "preflight", "--output", output.as_posix()], env={**os.environ, "BASE_URL": self.base},
                    capture_output=True, timeout=10)
                self.assertFalse((output / "zap-plan.yaml").exists())
                self.assertFalse(json.loads((output / "coverage.json").read_text())["complete"])
        finally:
            type(self).slow_root = False
        elapsed = time.monotonic() - started
        self.assertEqual(self.paths[start:], ["/"], completed.stderr.decode(errors="replace"))
        # MSYS coreutils reports the killed native Windows child's wait status directly.
        self.assertIn(completed.returncode, (124, 137, 2304) if os.name == "nt" else (124, 137),
                      completed.stderr.decode(errors="replace"))
        self.assertLess(elapsed, 7)


if __name__ == "__main__":
    unittest.main()
