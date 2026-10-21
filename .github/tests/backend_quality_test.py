from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def profile(pom, name):
    return pom.find(f"m:profiles/m:profile[m:id='{name}']", NS)


def plugin(parent, name):
    return parent.find(f"m:build/m:plugins/m:plugin[m:artifactId='{name}']", NS)


class BackendQualityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.parent = ET.parse(ROOT / "backend/pom.xml").getroot()
        cls.bootstrap = ET.parse(ROOT / "backend/blocbuy-bootstrap/pom.xml").getroot()
        cls.workflow = (ROOT / ".github/workflows/ci-backend.yml").read_text(encoding="utf-8")

    def test_ci_logging_is_quiet_without_changing_local_logging(self):
        self.assertIsNone(self.parent.find("m:properties/m:test.log.level", NS))
        ci = profile(self.parent, "ci")
        self.assertIsNotNone(ci)
        self.assertEqual(ci.findtext("m:properties/m:test.log.level", namespaces=NS), "INFO")
        for name in ["maven-surefire-plugin", "maven-failsafe-plugin"]:
            self.assertEqual(
                plugin(ci, name).findtext(
                    "m:configuration/m:systemPropertyVariables/m:logging.level.com.blocbuy", namespaces=NS
                ),
                "${test.log.level}",
            )

    def test_only_bootstrap_ci_integration_tests_use_two_isolated_forks(self):
        ci = profile(self.bootstrap, "ci")
        self.assertIsNotNone(ci)
        self.assertEqual(ci.findtext("m:properties/m:bootstrap.it.forks", namespaces=NS), "2")
        config = plugin(ci, "maven-failsafe-plugin").find("m:configuration", NS)
        self.assertEqual(config.findtext("m:forkCount", namespaces=NS), "${bootstrap.it.forks}")
        self.assertEqual(config.findtext("m:reuseForks", namespaces=NS), "true")
        self.assertEqual(config.findtext("m:runOrder", namespaces=NS), "reversealphabetical")
        self.assertIsNone(plugin(self.parent, "maven-failsafe-plugin").find("m:configuration/m:forkCount", NS))

    def test_private_stripe_database_remains_serial(self):
        private = profile(self.bootstrap, "private-stripe-test-db")
        self.assertIsNotNone(private)
        self.assertEqual(private.findtext("m:activation/m:property/m:name", namespaces=NS), "env.STRIPE_TEST_DB_URL")
        self.assertEqual(private.findtext("m:properties/m:bootstrap.it.forks", namespaces=NS), "1")
        profiles = self.bootstrap.findall("m:profiles/m:profile/m:id", NS)
        names = [element.text for element in profiles]
        self.assertGreater(names.index("private-stripe-test-db"), names.index("ci"))

    def test_ci_profiles_never_skip_tests_or_quality_gates(self):
        forbidden = {"skip", "skipTests", "skipITs", "excludes", "excludedGroups", "includes", "groups"}
        for pom in [self.parent, self.bootstrap]:
            ci = profile(pom, "ci")
            self.assertIsNotNone(ci)
            self.assertFalse({element.tag.split("}")[-1] for element in ci.iter()} & forbidden)
        modules = self.parent.find("m:modules", NS)
        self.assertEqual(len(modules), 10)
        self.assertIn("services/commerce-svc", [element.text for element in modules])

    def test_pr_and_dev_verification_use_ci_profile_without_test_selection(self):
        commands = re.findall(r"\./mvnw -B -T 2 verify[^\n]*(?:\n          -[^\n]*)*", self.workflow)
        self.assertEqual(len(commands), 2)
        for command in commands:
            self.assertIn("-Pci", command)
            for option in ["-DskipTests", "-DskipITs", "-Dit.test=", "-Dtest=", "-Djacoco.skip", "-pl "]:
                self.assertNotIn(option, command)

    def test_coverage_agent_keeps_the_existing_shared_append_file(self):
        jacoco = plugin(self.parent, "jacoco-maven-plugin")
        agent = jacoco.find("m:executions/m:execution[m:id='jacoco-prepare-agent']", NS)
        self.assertIsNotNone(agent)
        self.assertEqual(agent.findtext("m:configuration/m:append", namespaces=NS), "true")
        self.assertIsNone(agent.find("m:configuration/m:destFile", NS))
        self.assertIn("backend/blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml", self.workflow)

    def test_spotbugs_engine_and_annotations_share_one_pin_and_updates_are_grouped(self):
        spotbugs = self.parent.find(
            "m:build/m:pluginManagement/m:plugins/m:plugin[m:artifactId='spotbugs-maven-plugin']", NS
        )
        self.assertIsNotNone(spotbugs)
        self.assertEqual(spotbugs.findtext("m:version", namespaces=NS), "${spotbugs-maven-plugin.version}")
        self.assertEqual(
            spotbugs.findtext("m:dependencies/m:dependency[m:artifactId='spotbugs']/m:version", namespaces=NS),
            "${spotbugs.version}",
        )
        self.assertEqual(
            self.parent.findtext(
                "m:dependencyManagement/m:dependencies/m:dependency[m:artifactId='spotbugs-annotations']/m:version",
                namespaces=NS,
            ),
            "${spotbugs.version}",
        )
        dependabot = (ROOT / ".github/dependabot.yml").read_text(encoding="utf-8")
        self.assertRegex(dependabot, r"spotbugs:\s*\n\s*patterns: \['com\.github\.spotbugs:\*'\]")

    def test_pom_policy_checks_run_in_the_existing_backend_job(self):
        build_quality = self.workflow.split("  build-quality:\n", 1)[1].split("  contract-check:", 1)[0]
        self.assertIn("python3 -m unittest discover -s .github/tests -p 'backend*_test.py'", build_quality)
        security = (ROOT / ".github/workflows/security.yml").read_text(encoding="utf-8")
        workflow_filter = security.split("filters: |", 1)[1].split("deployment:", 1)[0]
        self.assertNotIn("backend/pom.xml", workflow_filter)
        self.assertNotIn("backend/**/pom.xml", workflow_filter)


if __name__ == "__main__":
    unittest.main()
