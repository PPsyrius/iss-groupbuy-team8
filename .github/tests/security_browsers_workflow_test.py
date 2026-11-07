"""Keep the supplemental browser workflow local-only and reproducible."""

import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class SecurityBrowsersWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = (ROOT / ".github/workflows/security-browsers.yml").read_text(encoding="utf-8")
        cls.recipe = (ROOT / "frontend/tooling/security-browsers.Dockerfile").read_text(encoding="utf-8")

    def test_dispatch_only_without_deployment_permissions_or_credentials(self):
        events = self.workflow.split("\non:\n", 1)[1].split("\npermissions:", 1)[0]
        self.assertEqual(events.strip(), "workflow_dispatch:")
        permissions = self.workflow.split("\npermissions:\n", 1)[1].split("\nconcurrency:", 1)[0]
        self.assertEqual(permissions.strip(), "contents: read")
        self.assertNotIn("secrets.", self.workflow)
        self.assertNotIn("id-token:", self.workflow)
        self.assertNotIn("AUDIT_BASELINE", self.workflow)
        self.assertNotIn("node /repo/audit.cjs", self.workflow)

    def test_workflow_uses_versioned_docker_recipe_for_checks_and_browsers(self):
        self.assertIn("docker build --file frontend/tooling/security-browsers.Dockerfile", self.workflow)
        self.assertIn("blocbuy-security-browsers:ci pnpm check", self.workflow)
        self.assertIn("target=/evidence", self.workflow)
        self.assertIn("E2E_SECURITY_BROWSERS=all", self.recipe)
        self.assertIn('"--workers=2"', self.recipe)

    def test_results_are_retained_even_after_browser_failure(self):
        upload = self.workflow.split("      - name: Retain browser reports and failure artifacts", 1)[1]
        self.assertIn("if: always()", upload)
        self.assertIn("retention-days: 14", upload)
        self.assertIn("browser-security-${{ github.sha }}", upload)
        for variable in ("PLAYWRIGHT_HTML_OUTPUT_DIR", "PLAYWRIGHT_JSON_OUTPUT_FILE"):
            self.assertIn(variable + "=/evidence/", self.recipe)
        self.assertIn('"--output=/evidence/test-results"', self.recipe)

    def test_tooling_has_pinned_inputs_standard_trust_and_all_engines(self):
        package = json.loads((ROOT / "frontend/package.json").read_text(encoding="utf-8"))
        self.assertIn("FROM node:24.21.0-slim@sha256:", self.recipe)
        self.assertIn("npm install --global " + package["packageManager"], self.recipe)
        self.assertIn("pnpm install --frozen-lockfile", self.recipe)
        self.assertIn("ca-certificates", self.recipe)
        self.assertIn("playwright install --with-deps chromium firefox webkit", self.recipe)
        self.assertNotIn(":local", self.recipe)
        self.assertNotIn("ignoreHTTPSErrors", self.recipe)

    def test_browser_runtime_is_non_root_with_accessible_binaries_and_outputs(self):
        self.assertIn("ENV PLAYWRIGHT_BROWSERS_PATH=/opt/playwright-browsers", self.recipe)
        self.assertLess(self.recipe.index("ENV PLAYWRIGHT_BROWSERS_PATH="), self.recipe.index("playwright install --with-deps"))
        self.assertIn("chown -R node:node src/api/generated node_modules/.vite node_modules/.vite-temp /evidence", self.recipe)
        self.assertNotIn("chown -R node:node /repo", self.recipe)
        self.assertIn("chmod a+r node_modules/.pnpm-workspace-state-v1.json", self.recipe)
        self.assertEqual([line for line in self.recipe.splitlines() if line.startswith("USER ")], ["USER node"])
        self.assertIn('sudo chown 1000:1000 "$EVIDENCE_DIR"', self.workflow)
        self.assertNotIn("--user root", self.workflow)
        self.assertNotIn("chmod 777", self.workflow)

    def test_write_boundary_is_executed_during_build_and_runtime(self):
        self.assertIn("RUN node tooling/verify-write-boundary.mjs", self.recipe)
        self.assertGreater(self.recipe.index("RUN node tooling/verify-write-boundary.mjs"), self.recipe.index("USER node"))
        self.assertIn("blocbuy-security-browsers:ci node tooling/verify-write-boundary.mjs", self.workflow)

    def test_regeneration_cleans_contents_without_removing_the_output_directory(self):
        package = json.loads((ROOT / "frontend/package.json").read_text(encoding="utf-8"))
        config = (ROOT / "frontend/openapi-ts.config.ts").read_text(encoding="utf-8")
        self.assertEqual(package["scripts"]["gen:api"], "node scripts/verify-generator-params.mjs && node scripts/clean-api-output.mjs && openapi-ts && node scripts/ts-nocheck-generated.mjs")
        self.assertIn('output: { path: "src/api/generated", clean: false }', config)
        self.assertIn("COPY frontend/scripts/clean-api-output.mjs scripts/clean-api-output.mjs", self.recipe)


if __name__ == "__main__":
    unittest.main()
