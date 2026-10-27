import json
from pathlib import Path
import unittest
from urllib.parse import urlsplit


ROOT = Path(__file__).resolve().parents[2]


class SecurityHeadersTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.policy = json.loads(
            (ROOT / "deployment/frontend/cloudfront/response-headers-policy.json").read_text(
                encoding="utf-8"
            )
        )
        csp = cls.policy["SecurityHeadersConfig"]["ContentSecurityPolicy"]
        cls.directives = {
            parts[0]: parts[1:]
            for directive in csp["ContentSecurityPolicy"].split(";")
            if (parts := directive.split())
        }

    def test_image_sources_are_exact_reviewed_origins(self):
        self.assertEqual(
            self.directives["img-src"],
            ["'self'", "https://dcsjrkmummxi7.cloudfront.net", "https://lh3.googleusercontent.com"],
        )
        for source in self.directives["img-src"][1:]:
            url = urlsplit(source)
            self.assertEqual(url.scheme, "https")
            self.assertTrue(url.hostname)
            self.assertNotIn("*", source)
            self.assertEqual(url.path, "")
            self.assertFalse(url.query or url.fragment or url.username or url.password or url.port)

    def test_other_csp_restrictions_remain_in_place(self):
        for name in ("default-src", "script-src", "connect-src", "font-src", "base-uri", "form-action"):
            self.assertEqual(self.directives[name], ["'self'"])
        for name in ("object-src", "frame-ancestors"):
            self.assertEqual(self.directives[name], ["'none'"])
        self.assertEqual(self.directives["upgrade-insecure-requests"], [])
        # #637 is separate work, not silently resolved by this image policy change.
        self.assertEqual(self.directives["style-src"], ["'self'", "'unsafe-inline'"])

    def test_security_headers_still_override_origin_values(self):
        for config in self.policy["SecurityHeadersConfig"].values():
            self.assertIs(config["Override"], True)
        self.assertEqual(self.policy["SecurityHeadersConfig"]["FrameOptions"]["FrameOption"], "DENY")

    def test_permissions_policy_denies_only_reviewed_unused_features(self):
        custom = self.policy["CustomHeadersConfig"]
        self.assertEqual(custom["Quantity"], len(custom["Items"]))
        self.assertEqual(len(custom["Items"]), 1)
        header = custom["Items"][0]
        self.assertEqual(header["Header"], "Permissions-Policy")
        self.assertIs(header["Override"], True)
        self.assertEqual(
            header["Value"], "camera=(), microphone=(), geolocation=(), payment=(), usb=()"
        )
        self.assertNotIn("clipboard", header["Value"])
        self.assertNotIn("web-share", header["Value"])

    def test_policy_changes_trigger_frontend_pr_validation(self):
        workflow = (ROOT / ".github/workflows/ci-frontend.yml").read_text(encoding="utf-8")
        frontend_filter = workflow.split("            frontend:\n", 1)[1].split("            intent:", 1)[0]
        self.assertIn("deployment/frontend/cloudfront/response-headers-policy.json", frontend_filter)

    def test_policy_inputs_trigger_static_policy_tests(self):
        workflow = (ROOT / ".github/workflows/security.yml").read_text(encoding="utf-8")
        workflow_filter = workflow.split("            workflow:\n", 1)[1].split("            deployment:", 1)[0]
        for path in ("deployment/frontend/cloudfront/response-headers-policy.json",
                     "infra/terraform/frontend-stack/cloudfront.tf",
                     "fullstack-localhost/cloudfront-headers.mjs"):
            self.assertIn(path, workflow_filter)


if __name__ == "__main__":
    unittest.main()
