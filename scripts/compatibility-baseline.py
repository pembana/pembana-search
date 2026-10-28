#!/usr/bin/env python3
"""Run the untouched Stage 0 corpus twice; preserve evidence without publishing."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import xml.etree.ElementTree as ET

UPSTREAM = "c4b404dc6cf22b540d3e7f6211e557f5d83b6c61"
ROOT = Path(__file__).resolve().parents[1]
INPUTS = ("src", "pom.xml", ".mvn", "mvnw", "mvnw.cmd", "Makefile", ".java-version")
INVENTORY = ROOT / "docs/baseline/scenarios.json"
MAVEN = ["./mvnw", "--batch-mode", "-Dgpg.skip=true", "-Ddependency-check.skip=true"]


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def manifest(source):
    files = []
    for name in INPUTS:
        path = source / name
        if path.is_dir():
            files.extend(p for p in path.rglob("*") if p.is_file())
        else:
            files.append(path)
    return {str(p.relative_to(source)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(files) if p.is_file()}


def inventory(source):
    """The pinned corpus has simple, unparameterized Kotlin @Test methods."""
    cases = []
    for path in sorted((source / "src/test").rglob("*.kt")):
        text = path.read_text()
        package = re.search(r"^package (\S+)", text, re.M)
        cls = re.search(r"^class (\w+)", text, re.M)
        methods = re.findall(r"@Test\s+fun (\w+)\(\)", text)
        if methods and not (package and cls):
            raise ValueError(f"Cannot identify test class: {path}")
        cases.extend({"class": f"{package[1]}.{cls[1]}", "method": name} for name in methods)
    return cases


def command(argv, cwd, log):
    with log.open("w") as stream:
        stream.write("Command: " + " ".join(argv) + "\n")
        stream.flush()
        try:
            return subprocess.run(argv, cwd=cwd, stdout=stream, stderr=subprocess.STDOUT).returncode
        except OSError as exc:
            stream.write(str(exc) + "\n")
            return 127


def read_results(source):
    results = []
    for report in sorted((source / "target/surefire-reports").glob("TEST-*.xml")):
        for case in ET.parse(report).getroot().iter("testcase"):
            outcome = next((name for name in ("failure", "error", "skipped")
                            if case.find(name) is not None), "passed")
            problem = case.find(outcome) if outcome != "passed" else None
            results.append({"class": case.attrib["classname"], "method": case.attrib["name"],
                            "outcome": outcome,
                            "detail": problem.attrib.get("message", "") if problem is not None else ""})
    return results


def build_outcome(code, log, results):
    if code == 0:
        return "passed"
    text = log.read_text(errors="replace")
    if any(c["outcome"] in ("failure", "error") for c in results):
        return "test-failure"
    if "jacoco-maven-plugin" in text and "Coverage checks have not been met" in text:
        return "coverage-check-failure"
    if "Compilation failure" in text or "Compilation error" in text:
        return "compilation-failure"
    if any(s in text for s in ("could not be resolved", "Could not resolve", "Could not transfer",
                               "Unknown host", "Could not find or load main class")):
        return "infrastructure-or-dependency-failure"
    return "other-build-failure"


def run_suite(label, source, output, expected):
    dest = output / label
    dest.mkdir()
    print(f"Running {label}: original clean verify lifecycle (log: {dest / 'build.log'})", flush=True)
    before = manifest(source)
    write_json(dest / "inputs.json", before)
    command(["./mvnw", "--version"], source, dest / "maven-version.log")
    # If clean itself fails, reports from an earlier invocation must not look like
    # a fresh successful run. Only discard generated report directories.
    for name in ("surefire-reports", "test-results"):
        path = source / "target" / name
        if path.exists():
            shutil.rmtree(path)
    code = command(MAVEN + ["clean", "verify"], source, dest / "build.log")
    results = read_results(source)
    for name in ("surefire-reports", "test-results"):
        path = source / "target" / name
        if path.exists():
            shutil.copytree(path, dest / name)
    # Reporting goals capture inherited configuration and resolved transitive versions
    # even when verify fails. They do not modify the upstream POM.
    tree_code = command(MAVEN + ["org.apache.maven.plugins:maven-dependency-plugin:3.6.1:tree",
                               "-Dverbose", f"-DoutputFile={dest / 'dependencies.txt'}"],
                        source, dest / "dependency-tree.log")
    pom_code = command(MAVEN + ["org.apache.maven.plugins:maven-help-plugin:3.2.0:effective-pom",
                              f"-Doutput={dest / 'effective-pom.xml'}"],
                       source, dest / "effective-pom.log")
    counts = Counter(c["outcome"] for c in results)
    ids = [(c["class"], c["method"]) for c in results]
    wanted = [(c["class"], c["method"]) for c in expected]
    summary = {
        "build_exit_code": code, "build_outcome": build_outcome(code, dest / "build.log", results),
        "source_inventory_count": len(expected), "discovered_in_reports": len(results),
        "executed": len(results) - counts["skipped"], "passed": counts["passed"],
        "failed": counts["failure"], "errored": counts["error"], "skipped": counts["skipped"],
        "missing": sorted(set(wanted) - set(ids)), "unexpected": sorted(set(ids) - set(wanted)),
        "duplicate_results": len(ids) != len(set(ids)), "inputs_unchanged": before == manifest(source),
        "dependency_tree_exit_code": tree_code, "effective_pom_exit_code": pom_code,
        "coverage_report_present": (dest / "test-results/coverage/jacoco/jacoco.xml").is_file(),
        "scenarios": results,
    }
    write_json(dest / "summary.json", summary)
    print(f"{label}: {summary['build_outcome']}; executed={summary['executed']}, "
          f"failed={summary['failed']}, errored={summary['errored']}, skipped={summary['skipped']}", flush=True)
    return summary


def compare_runs(upstream, imported, output):
    def outcomes(run):
        return {(c["class"], c["method"]): c["outcome"] for c in run["scenarios"]}

    equivalent = outcomes(upstream) == outcomes(imported)
    trees = [output / label / "dependencies.txt" for label in ("upstream", "imported")]
    same_dependencies = all(p.is_file() for p in trees) and trees[0].read_text() == trees[1].read_text()
    complete = all(r["discovered_in_reports"] == 113 and not r["missing"] and not r["unexpected"]
                   and not r["duplicate_results"] and r["inputs_unchanged"] and r["skipped"] == 0
                   for r in (upstream, imported))
    successful = all(r["build_exit_code"] == 0 and r["dependency_tree_exit_code"] == 0
                     and r["effective_pom_exit_code"] == 0 and r["coverage_report_present"]
                     for r in (upstream, imported))
    return {"same_scenario_outcomes": equivalent, "complete_corpus": complete,
            "same_resolved_dependencies": same_dependencies,
            "builds_and_evidence_successful": successful,
            "gate_passed": equivalent and complete and successful and same_dependencies}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "build/baseline")
    output = parser.parse_args().output.resolve()
    # Refuse stale evidence and paths erased by Maven clean or overlapping source inputs.
    if output == ROOT or output == ROOT / "target" or ROOT / "target" in output.parents:
        parser.error("Use an evidence directory outside target/ and the repository root")
    if any(output == ROOT / p or ROOT / p in output.parents for p in INPUTS):
        parser.error("The evidence directory cannot be inside a baseline source input")
    if output.exists() and any(output.iterdir()):
        parser.error("Output directory must be empty; choose a new --output to preserve prior evidence")
    output.mkdir(parents=True, exist_ok=True)
    write_json(output / "environment.json", {
        "upstream_revision": UPSTREAM, "imported_revision": git("rev-parse", "HEAD"),
        "started_at_utc": datetime.now(timezone.utc).isoformat(),
        "platform": platform.platform(), "machine": platform.machine(),
        "artifact": "com.sipios:spring-search:0.3.0", "java_home": os.environ.get("JAVA_HOME"),
        "working_tree_status": git("status", "--short"), "python": platform.python_version(),
    })
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if os.environ.get("JAVA_HOME") else "java"
    command([java, "-version"], ROOT, output / "java-version.log")
    if not re.search(r'version "17[.\"]', (output / "java-version.log").read_text()):
        parser.error("Select JDK 17 using JAVA_HOME and PATH before running the baseline")
    (output / "import-diff.txt").write_text(git("diff", "--stat", UPSTREAM, "HEAD") + "\n")
    with tempfile.TemporaryDirectory(prefix="pembana-search-upstream-") as tmp:
        source = Path(tmp) / "source"
        source.mkdir()
        archive = Path(tmp) / "upstream.tar"
        with archive.open("wb") as stream:
            subprocess.run(["git", "archive", UPSTREAM], cwd=ROOT, stdout=stream, check=True)
        with tarfile.open(archive) as tar:
            tar.extractall(source, filter="data")
        expected = json.loads(INVENTORY.read_text())
        if len(expected) != 113 or inventory(source) != expected or inventory(ROOT) != expected:
            raise ValueError("Scenario inventory must match all 113 pinned upstream methods")
        same_inputs = manifest(ROOT) == manifest(source)
        write_json(output / "input-comparison.json", {"identical": same_inputs,
                   "upstream": manifest(source), "imported": manifest(ROOT)})
        if not same_inputs:
            raise ValueError("Stage 0 inputs differ from the pinned upstream source")
        shutil.copyfile(INVENTORY, output / "scenarios.json")
        upstream = run_suite("upstream", source, output, expected)
        imported = run_suite("imported", ROOT, output, expected)
    comparison = compare_runs(upstream, imported, output)
    write_json(output / "comparison.json", comparison)
    print(json.dumps(comparison, indent=2), flush=True)
    return 0 if comparison["gate_passed"] else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, subprocess.SubprocessError, ET.ParseError) as exc:
        print(f"Baseline infrastructure/evidence error: {exc}", file=sys.stderr)
        sys.exit(2)
