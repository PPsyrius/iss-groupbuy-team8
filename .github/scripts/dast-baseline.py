"""Prepare read-only ZAP traffic and verify coverage from ZAP's own HAR export."""

import argparse
import base64
from collections import Counter
from datetime import datetime, timezone
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError
from urllib.parse import parse_qsl, urlencode, urlsplit
from urllib.request import build_opener, HTTPRedirectHandler, ProxyHandler, Request
from uuid import UUID


SUCCESS = "COMMON_200_000"
MAX_RESPONSE_BYTES = 2 * 1024 * 1024
MAX_HAR_BYTES = 64 * 1024 * 1024
# Minimum response-level policy: cookies, headers, CSP and information disclosure.
# Other installed/enabled rules also run and must have execution evidence.
REQUIRED_PASSIVE_RULES = (10010, 10011, 10015, 10020, 10021, 10035, 10036, 10038, 10054, 10055, 10063, 90004)


class ScanError(ValueError):
    pass


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class SpaMarkup(HTMLParser):
    def __init__(self):
        super().__init__()
        self.root = False
        self.module = False

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        self.root |= tag == "div" and attrs.get("id") == "root"
        self.module |= tag == "script" and attrs.get("type") == "module" and bool(attrs.get("src"))


def origin(value):
    """Require an origin, never credentials, query parameters or an application path."""
    parsed = urlsplit(value.strip())
    if (parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username is not None
            or parsed.password is not None or parsed.path not in ("", "/") or parsed.query or parsed.fragment
            or any(character.isspace() or ord(character) < 32 for character in value)):
        raise ScanError("BASE_URL must be an HTTP(S) origin without credentials, a path, query or fragment")
    host = parsed.hostname.lower().encode("idna").decode("ascii")
    if not re.fullmatch(r"[a-z0-9.:-]+", host):
        raise ScanError("BASE_URL has an invalid hostname")
    port = parsed.port
    if port is not None and not 1 <= port <= 65535:
        raise ScanError("BASE_URL has an invalid port")
    if ":" in host:
        host = f"[{host}]"
    if port is not None and (parsed.scheme, port) not in (("https", 443), ("http", 80)):
        host += f":{port}"
    return f"{parsed.scheme}://{host}"


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")


def fetch(url):
    # No ambient proxy, cookies, credentials or redirects, especially to OAuth providers.
    opener = build_opener(ProxyHandler({}), NoRedirect())
    request = Request(url, headers={"Accept": "text/html,application/json", "User-Agent": "BlocBuy-DAST-preflight"})
    try:
        response = opener.open(request, timeout=10)
    except HTTPError as error:
        response = error
    with response:
        body = response.read(MAX_RESPONSE_BYTES + 1)
        if len(body) > MAX_RESPONSE_BYTES:
            raise ScanError("Preflight response exceeds the two-MiB limit")
        return response.code, response.headers.get("Content-Type", ""), body.decode("utf-8")


def validate_response(probe, status, content_type, body):
    if status != probe["status"]:
        raise ScanError(f"expected HTTP {probe['status']}, received {status}")
    media_type = content_type.split(";", 1)[0].strip().lower()
    if probe["kind"] == "html":
        markup = SpaMarkup()
        markup.feed(body)
        if media_type != "text/html" or not markup.root or not markup.module:
            raise ScanError("expected SPA HTML with its root mount and module script")
        return None
    if media_type != "application/json":
        raise ScanError("expected application/json, not an HTML fallback or login page")
    try:
        payload = json.loads(body)
    except json.JSONDecodeError as error:
        raise ScanError("response is not valid JSON") from error
    if not isinstance(payload, dict) or payload.get("code") != probe["code"]:
        raise ScanError(f"expected Result code {probe['code']}")
    data = payload.get("data")
    if probe["kind"] == "page":
        if (not isinstance(data, dict) or not isinstance(data.get("items"), list)
                or any(not isinstance(item, dict) for item in data["items"])
                or type(data.get("total")) is not int or data["total"] < 0
                or type(data.get("page")) is not int or data["page"] != probe["page"]
                or type(data.get("size")) is not int or data["size"] < 1):
            raise ScanError("expected a catalog page with items, total, page and size")
        if "size" in probe and data["size"] != probe["size"]:
            raise ScanError("catalog pagination size was not applied")
    elif probe["kind"] == "detail":
        if not isinstance(data, dict) or data.get("id") != probe["id"]:
            raise ScanError("expected the selected product detail")
    elif data is not None:
        raise ScanError("error/denial response must not contain successful data")
    return payload


def probe(name, url, kind, status=200, code=SUCCESS, **expected):
    return {"name": name, "method": "GET", "url": url, "kind": kind,
            "status": status, "code": code, **expected}


def required_requests(base, catalog):
    items = catalog["data"]["items"]
    product_id, keyword, category = "{existingId}", "BlocBuy", "dast-unlisted-category"
    unavailable = {"unavailable": "Catalog is empty; an existing product fixture is unavailable"} if not items else {}
    if items:
        product = items[0]
        try:
            product_id = str(UUID(product["id"]))
        except (KeyError, ValueError, TypeError, AttributeError) as error:
            raise ScanError("Catalog product fixture must have a valid UUID") from error
        if not isinstance(product.get("title"), str) or not product["title"].strip():
            raise ScanError("Catalog product fixture must have a title for search coverage")
        word = re.search(r"\w+", product["title"])
        keyword = word.group()[:32] if word else keyword
        category = product.get("category") or category
        if not isinstance(category, str):
            raise ScanError("Catalog product category must be a string or null")
    products = base + "/api/v1/products"
    return [
        probe("SPA root", base + "/", "html"),
        probe("Catalog", products, "page", page=1),
        probe("Pagination", products + "?page=2&size=1", "page", page=2, size=1),
        probe("Category filter", products + "?" + urlencode({"category": category}), "page", page=1),
        probe("Keyword search", products + "?" + urlencode({"keyword": keyword, "ai": "false"}), "page", page=1),
        probe("Product detail", products + "/" + product_id, "detail", id=product_id, **unavailable),
        probe("SPA product deep link", base + "/products/" + product_id, "html", **unavailable),
        probe("Invalid pagination", products + "?page=0", "error", 400, "COMMON_400_002"),
        probe("Anonymous orders denial", base + "/api/v1/orders/me", "error", 401, "COMMON_401_001"),
        probe("Anonymous admin denial", base + "/api/v1/admin/products", "error", 401, "COMMON_401_001"),
    ]


def passive_snapshot(filename):
    """Read ZAP runtime state inside the same process; never infer execution from alerts."""
    script = """
var loader = Java.type('org.parosproxy.paros.control.Control').getSingleton().getExtensionLoader();
var pscan = loader.getExtension('ExtensionPassiveScan2');
var stats = loader.getExtension('ExtensionStats').getInMemoryStats();
if (pscan === null || stats === null) throw new Error('Passive scanner/statistics unavailable');
function stat(key) {
    var value = stats.getStat(key);
    return value === null ? null : Number(value);
}
var rules = [], installed = pscan.getPassiveScannersManager().getScanRules();
for (var i = 0; i < installed.size(); i++) {
    var rule = installed.get(i);
    rules.push({id: rule.getPluginId(), name: String(rule.getName()), enabled: rule.isEnabled(),
        threshold: String(rule.getAlertThreshold()), time_ms: stat('stats.pscan.' + rule.getPluginId() + '.time')});
}
var errors = 0, counters = stats.getStats('stats.log.error.org.zaproxy.addon.pscan').values().iterator();
while (counters.hasNext()) errors += Number(counters.next());
var data = {schema: 1, queued: pscan.getRecordsToScan(), rules: rules, scanner_errors: errors,
    request_bodies_skipped: stat('stats.pscan.reqBodyTooBig') || 0,
    response_bodies_skipped: stat('stats.pscan.respBodyTooBig') || 0};
Java.type('java.nio.file.Files').writeString(Java.type('java.nio.file.Paths').get(OUTPUT), JSON.stringify(data));
""".replace("OUTPUT", json.dumps(filename))
    name = filename.rsplit("/", 1)[-1]
    # The bundled Scripts add-on requires inline scripts to be added before running by name.
    return [
        {"type": "script", "name": "Load " + name, "alwaysRun": True,
         "parameters": {"action": "add", "type": "standalone", "engine": "ECMAScript : Graal.js",
                        "name": name, "inline": script}},
        {"type": "script", "name": "Record " + name, "alwaysRun": True,
         "parameters": {"action": "run", "type": "standalone", "name": name}},
    ]


def automation_plan(base, probes):
    # Separate crawl scope prevents the spider from following OAuth, admin or payment links.
    crawl_paths = ["^" + re.escape(base) + pattern + "$" for pattern in (
        r"/(?:\?.*)?", r"/assets/.*", r"/products/[a-f0-9-]+(?:\?.*)?",
        r"/(?:favicon\.(?:ico|svg)|apple-touch-icon\.png|manifest\.webmanifest|robots\.txt|sitemap\.xml)",
    )]
    jobs = [
        # ZAP disables rules at a positive alert cap; keep them on until the bounded scan finishes.
        {"type": "passiveScan-config", "parameters": {"enableTags": False, "maxAlertsPerRule": 0,
            "disableAllRules": False}, "rules": [{"id": rule, "threshold": "Medium"} for rule in REQUIRED_PASSIVE_RULES]},
        *passive_snapshot("/zap/wrk/passive-start.json"),
        {"type": "requestor", "requests": [
            {"name": item["name"], "url": item["url"], "method": "GET", "responseCode": item["status"],
             "headers": ["Accept: text/html" if item["kind"] == "html" else "Accept: application/json"]}
            for item in probes if not item.get("unavailable")
        ]},
        {"type": "spider", "parameters": {"context": "public-crawl", "url": base + "/", "maxDuration": 3,
            "maxDepth": 3, "threadCount": 2, "processForm": False, "postForm": False}},
        {"type": "passiveScan-wait", "parameters": {"maxDuration": 3}, "alwaysRun": True},
        *passive_snapshot("/zap/wrk/passive-finish.json"),
        {"type": "export", "parameters": {"context": "target", "type": "har", "source": "all",
            "fileName": "/zap/wrk/traffic.har"}, "alwaysRun": True},
    ]
    for extension, template in (("html", "traditional-html"), ("json", "traditional-json"), ("md", "traditional-md")):
        jobs.append({"type": "report", "parameters": {"template": template, "reportDir": "/zap/wrk",
            "reportFile": "report." + extension, "reportTitle": "BlocBuy passive DAST"}, "alwaysRun": True})
    return {"env": {"contexts": [
        {"name": "target", "urls": [base + "/"], "includePaths": ["^" + re.escape(base) + r"/.*$"]},
        # Context URLs also imply wildcard includes, so explicit exclusions enforce the allowlist.
        {"name": "public-crawl", "urls": [base + "/"], "includePaths": crawl_paths,
         "excludePaths": ["^(?!(?:" + "|".join(crawl_paths) + ")).*"]},
    ], "parameters": {"failOnError": True, "failOnWarning": False, "continueOnFailure": False,
        "progressToStdout": True}}, "jobs": jobs}


def preflight(output, base_url, get=fetch):
    output.mkdir(parents=True, exist_ok=True)
    if any(output.iterdir()):
        raise ScanError("Use an empty output directory to prevent stale coverage/report reuse")
    coverage = {"complete": False, "phase": "preflight", "checks": [],
                "errors": ["Preflight did not finish; check the step for a target error or its 30-second deadline"]}
    write_json(output / "coverage.json", coverage)
    base = origin(base_url)
    root = probe("SPA root", base + "/", "html")
    catalog = probe("Catalog", base + "/api/v1/products", "page", page=1)
    observations = []
    payload = None
    for item in (root, catalog):
        status, content_type, body = get(item["url"])
        payload = validate_response(item, status, content_type, body)
        observations.append({"url": item["url"], "method": "GET", "status": status, "content_type": content_type})
    probes = required_requests(base, payload)
    write_json(output / "preflight.json", {"origin": base, "requests": observations})
    write_json(output / "requests.json", {"origin": base, "requests": probes,
        "workflow_sha": os.environ.get("GITHUB_SHA"), "created_at": datetime.now(timezone.utc).isoformat()})
    # JSON is valid YAML; generating it avoids adding a PyYAML runtime dependency.
    write_json(output / "zap-plan.yaml", automation_plan(base, probes))
    write_json(output / "coverage.json", {**coverage, "phase": "awaiting ZAP", "errors": [], "expected": len(probes),
        "unavailable": [item["name"] for item in probes if item.get("unavailable")]})


def request_key(method, url):
    parsed = urlsplit(url)
    return method, origin(f"{parsed.scheme}://{parsed.netloc}"), parsed.path, tuple(sorted(parse_qsl(parsed.query, keep_blank_values=True)))


def har_body(entry):
    content = entry["response"]["content"]
    text = content["text"]
    if content.get("encoding") == "base64":
        text = base64.b64decode(text, validate=True).decode("utf-8")
    if not isinstance(text, str) or len(text.encode("utf-8")) > MAX_RESPONSE_BYTES:
        raise ScanError("Recorded response body is missing or too large")
    return text


def verify_passive(output):
    snapshots = [json.loads((output / f"passive-{phase}.json").read_text(encoding="utf-8"))
                 for phase in ("start", "finish")]
    inventories = []
    for snapshot in snapshots:
        if snapshot.get("schema") != 1 or not isinstance(snapshot.get("rules"), list):
            raise ScanError("Passive scanner snapshot has an unsupported schema")
        for field in ("queued", "scanner_errors", "request_bodies_skipped", "response_bodies_skipped"):
            if type(snapshot.get(field)) is not int or snapshot[field] != 0:
                raise ScanError(f"Passive scanner {field} must be zero, received {snapshot.get(field)}")
        rules = {rule["id"]: rule for rule in snapshot["rules"]}
        if len(rules) != len(snapshot["rules"]):
            raise ScanError("Passive scanner snapshot has duplicate rule IDs")
        inventories.append(rules)
    before, after = inventories
    enabled = {rule_id for rule_id, rule in before.items() if rule.get("enabled") is True}
    missing = set(REQUIRED_PASSIVE_RULES) - enabled
    if missing:
        raise ScanError("Required passive rules are missing or disabled: " + ", ".join(map(str, sorted(missing))))
    for rule_id in sorted(enabled):
        start, finish = before[rule_id], after.get(rule_id, {})
        if (start.get("threshold") not in ("LOW", "MEDIUM", "HIGH") or finish.get("enabled") is not True
                or finish.get("threshold") != start["threshold"]):
            raise ScanError(f"Passive rule {rule_id} is disabled or changed during the scan")
        # ZAP records this counter after a successful invocation, even if it took 0 ms.
        # Missing is not zero. A pre-existing counter is not evidence from this scan.
        if (start.get("time_ms") is not None or type(finish.get("time_ms")) is not int
                or finish["time_ms"] < 0):
            raise ScanError(f"Passive rule {rule_id} lacks fresh execution evidence")
    return {"verified": True, "enabled_rules": len(enabled), "executed_rules": len(enabled),
            "required_rule_ids": list(REQUIRED_PASSIVE_RULES), "queued": snapshots[1]["queued"]}


def verify(output, scan_outcome):
    result = {"complete": False, "phase": "ZAP coverage", "checks": [], "errors": [], "visited": [],
              "unfetched_nodes": 0}
    har = output / "traffic.har"
    try:
        manifest = json.loads((output / "requests.json").read_text(encoding="utf-8"))
        result["origin"] = manifest["origin"]
        result["expected"] = len(manifest["requests"])
        result["unavailable"] = [item["name"] for item in manifest["requests"] if item.get("unavailable")]
        if scan_outcome != "success":
            result["errors"].append("ZAP did not finish successfully; findings/coverage may be incomplete")
        if har.stat().st_size > MAX_HAR_BYTES:
            raise ScanError("ZAP traffic export exceeds the 64-MiB limit")
        entries = json.loads(har.read_text(encoding="utf-8"))["log"]["entries"]
        recorded = {}
        for entry in entries:
            request, response = entry["request"], entry["response"]
            key = request_key(request["method"], request["url"])
            if key[1] != manifest["origin"] or request["method"] != "GET":
                raise ScanError("ZAP recorded traffic outside the same-origin, GET-only scope")
            # source=all also exports synthetic parent nodes with no HTTP response.
            # They are discovery placeholders, not successful or failed HTTP traffic.
            if response["status"] == 0:
                result["unfetched_nodes"] += 1
                continue
            content_type = next((header["value"] for header in response["headers"]
                                 if header["name"].lower() == "content-type"), "")
            recorded.setdefault(key, []).append((entry, content_type))
            result["visited"].append({"method": request["method"], "url": request["url"],
                "status": response["status"], "content_type": content_type})
        for item in manifest["requests"]:
            check = {"name": item["name"], "method": "GET", "url": item["url"], "expected_status": item["status"],
                     "passed": False, "observed_statuses": []}
            if item.get("unavailable"):
                check.update(unavailable=True, error=item["unavailable"])
                result["checks"].append(check)
                continue
            matches = recorded.get(request_key("GET", item["url"]), [])
            check["observed_statuses"] = sorted({entry["response"]["status"] for entry, _ in matches})
            if not matches:
                check["error"] = "Request is absent from ZAP traffic"
            else:
                # Validate every recorded HTTP response, not one lucky response among failures.
                try:
                    for entry, content_type in matches:
                        validate_response(item, entry["response"]["status"], content_type, har_body(entry))
                    check["passed"] = True
                except (ScanError, KeyError, ValueError, UnicodeError) as error:
                    check["error"] = str(error)
            result["checks"].append(check)
        for extension in ("html", "json", "md"):
            report = output / f"report.{extension}"
            if not report.is_file() or report.stat().st_size == 0:
                raise ScanError(f"ZAP report.{extension} is missing or empty")
        report = json.loads((output / "report.json").read_text(encoding="utf-8"))
        if not isinstance(report, dict) or not isinstance(report.get("site"), list) or not report["site"]:
            raise ScanError("ZAP JSON report has no scanned site")
        result["alerts"] = dict(Counter(alert["riskcode"] for site in report["site"] for alert in site.get("alerts", [])))
        result["passive_scan"] = verify_passive(output)
    except (ScanError, OSError, ValueError, KeyError, TypeError, AttributeError, UnicodeError) as error:
        result["errors"].append(str(error))
    finally:
        # Never upload raw response bodies/cookies; preserve only the sanitized coverage inventory.
        har.unlink(missing_ok=True)
    result["covered"] = sum(check["passed"] for check in result["checks"])
    result["complete"] = (not result["errors"] and result.get("expected", 0) > 0
                          and result["covered"] == result["expected"])
    write_json(output / "coverage.json", result)
    return result


def markdown(result):
    state = "COMPLETE" if result["complete"] else "INCOMPLETE"
    lines = ["## Passive DAST: " + state, "", f"Required requests covered: {result.get('covered', 0)}/{result.get('expected', 0)}.",
             "", "| Check | Method | URL | Expected | Observed | Result |", "|---|---|---|---|---|---|"]
    for check in result.get("checks", []):
        observed = ", ".join(map(str, check["observed_statuses"])) or "not visited"
        status = "UNAVAILABLE" if check.get("unavailable") else "PASS" if check["passed"] else "FAIL"
        lines.append(f"| {check['name']} | GET | {check['url']} | {check['expected_status']} | {observed} | {status} |")
    for check in result.get("checks", []):
        if not check["passed"]:
            lines.append(f"\n{check['name']}: {check['error']}")
    for error in result.get("errors", []):
        lines.append("\nScan incomplete: " + error.replace("\n", " "))
    passive = result.get("passive_scan")
    if passive:
        lines.append(f"\nPassive processing verified: {passive['executed_rules']}/{passive['enabled_rules']} enabled rules ran; queue drained.")
    else:
        lines.append("\nPassive processing NOT VERIFIED; request coverage alone is not a completed security scan.")
    if "alerts" in result:
        counts = result["alerts"]
        lines.append(f"\nFindings (advisory): {counts.get('3', 0)} High, {counts.get('2', 0)} Medium, {counts.get('1', 0)} Low, {counts.get('0', 0)} Informational.")
    lines.append("\nSee the `zap-baseline` artifact for reports and `coverage.json` for visited URLs/methods/statuses. Static assets are not counted as required application coverage. No browser execution, active attacks or authenticated coverage is claimed.\n")
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("preflight", "verify"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--scan-outcome", default="skipped", choices=("success", "failure", "skipped", "cancelled"))
    parser.add_argument("--summary", type=Path)
    args = parser.parse_args(argv)
    if args.command == "preflight":
        fresh = False
        try:
            fresh = not args.output.exists() or (args.output.is_dir() and not any(args.output.iterdir()))
            preflight(args.output, os.environ.get("BASE_URL", ""))
            return 0
        except (ScanError, OSError, ValueError, UnicodeError) as error:
            result = {"complete": False, "phase": "preflight", "checks": [], "errors": [str(error)]}
            if fresh and args.output.is_dir():
                write_json(args.output / "coverage.json", result)
            print("Preflight failed: " + str(error), file=sys.stderr)
            return 1
    if (args.output / "requests.json").is_file():
        result = verify(args.output, args.scan_outcome)
    else:
        path = args.output / "coverage.json"
        result = json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {
            "complete": False, "checks": [], "errors": ["Preflight did not produce a request manifest"]}
    text = markdown(result)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "coverage.md").write_text(text, encoding="utf-8")
    if args.summary:
        with args.summary.open("a", encoding="utf-8") as summary:
            summary.write(text)
    print(text)
    return 0 if result["complete"] else 1


if __name__ == "__main__":
    sys.exit(main())
