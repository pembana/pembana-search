"""Regression checks for evidence integrity; these do not replace the upstream corpus."""

import copy
import json
from pathlib import Path
import runpy
import tempfile
import unittest

BASELINE = runpy.run_path(str(Path(__file__).with_name("compatibility-baseline.py")))


class EvidenceTests(unittest.TestCase):
    def test_report_statuses_and_method_identity(self):
        # A successful testcase and three different non-success outcomes must remain distinct.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            reports = root / "target/surefire-reports"
            reports.mkdir(parents=True)
            (reports / "TEST-example.xml").write_text('''<testsuite>
              <testcase classname="Example" name="passes"/>
              <testcase classname="Example" name="fails"><failure message="assertion"/></testcase>
              <testcase classname="Example" name="errors"><error message="bootstrap"/></testcase>
              <testcase classname="Example" name="skips"><skipped/></testcase>
            </testsuite>''')
            cases = BASELINE["read_results"](root)
            self.assertEqual([(c["method"], c["outcome"]) for c in cases],
                             [("passes", "passed"), ("fails", "failure"),
                              ("errors", "error"), ("skips", "skipped")])
            self.assertEqual(cases[1]["detail"], "assertion")

    def test_failure_categories(self):
        with tempfile.TemporaryDirectory() as tmp:
            log = Path(tmp) / "build.log"
            for message, expected in [
                ("jacoco-maven-plugin Coverage checks have not been met", "coverage-check-failure"),
                ("Compilation failure", "compilation-failure"),
                ("Could not transfer artifact", "infrastructure-or-dependency-failure"),
                ("Unexpected packaging failure", "other-build-failure"),
            ]:
                with self.subTest(expected=expected):
                    log.write_text(message)
                    self.assertEqual(BASELINE["build_outcome"](1, log, []), expected)
            self.assertEqual(BASELINE["build_outcome"](1, log, [{"outcome": "failure"}]), "test-failure")

    def test_gate_rejects_incomplete_or_misleading_evidence(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp)
            for label in ("upstream", "imported"):
                (output / label).mkdir()
                (output / label / "dependencies.txt").write_text("same dependencies\n")
            scenarios = json.loads(BASELINE["INVENTORY"].read_text())
            good = {"scenarios": [dict(c, outcome="passed") for c in scenarios],
                    "discovered_in_reports": 113, "missing": [], "unexpected": [],
                    "duplicate_results": False, "inputs_unchanged": True, "skipped": 0,
                    "build_exit_code": 0, "dependency_tree_exit_code": 0,
                    "effective_pom_exit_code": 0, "coverage_report_present": True}
            compare = BASELINE["compare_runs"]
            self.assertTrue(compare(good, good, output)["gate_passed"])
            for field, value in [("discovered_in_reports", 112), ("missing", ["lost test"]),
                                 ("unexpected", ["replacement test"]), ("duplicate_results", True),
                                 ("inputs_unchanged", False), ("skipped", 1), ("build_exit_code", 1),
                                 ("dependency_tree_exit_code", 1), ("effective_pom_exit_code", 1),
                                 ("coverage_report_present", False)]:
                with self.subTest(field=field):
                    bad = dict(good, **{field: value})
                    self.assertFalse(compare(good, bad, output)["gate_passed"])
            changed = copy.deepcopy(good)
            changed["scenarios"][0]["outcome"] = "failure"
            self.assertFalse(compare(good, changed, output)["gate_passed"])
            (output / "imported/dependencies.txt").write_text("different versions\n")
            self.assertFalse(compare(good, good, output)["gate_passed"])


if __name__ == "__main__":
    unittest.main()
