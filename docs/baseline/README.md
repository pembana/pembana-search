# Stage 0: upstream compatibility baseline

Status: **passed locally on 2026-10-28**. The Java production-code port has not
started. This milestone establishes AC-01; it does not establish the later Java,
framework, PostgreSQL, or matching-policy acceptance criteria.

## Provenance and unchanged inputs

The source baseline is Spring Search revision
`c4b404dc6cf22b540d3e7f6211e557f5d83b6c61` from
[theodo-fintech/spring-search](https://github.com/theodo-fintech/spring-search/tree/c4b404dc6cf22b540d3e7f6211e557f5d83b6c61),
whose POM declares `com.sipios:spring-search:0.3.0`. Pembana's inspected application
POM also declares that artifact version. Its newer Spring Boot version is not the
framework baseline used for this comparison.

Import commit `bbbb8572fbec180dda14bb1f69c3f4f6e7b2f781` differs from upstream only by
adding the Apache `LICENSE`, adding ignore rules, and removing
`.jpb/jpb-settings.xml`. The imported production sources, grammar, tests, fixtures,
Maven configuration and wrapper, Java-version file, and Makefile are unchanged.
The runner compares SHA-256 manifests of those inputs before running and checks
them again after each build. The committed inventory identifies all 113 tests by
fully qualified class and method name; the runtime reports must match those
identities, not merely their count.

The local Maven cache contains the binary, source JAR, and POM retrieved from
Maven Central. Their SHA-256 fingerprints are recorded in
[artifact-provenance.json](artifact-provenance.json). All 25 handwritten Kotlin
production files in the cached source JAR match the pinned source byte for byte.
This establishes source provenance; it does not claim a reproducible binary build
or authenticate the cached binary through an independent signature verification.

## Recorded execution

Both runs used the same machine and environment, with the pinned upstream run
executed first in a temporary source directory extracted from Git. The imported
run used the working checkout. Neither run installed or deployed an artifact.

| Setting | Baseline |
| --- | --- |
| JDK | BellSoft OpenJDK 17.0.20+10-LTS |
| OS / architecture | macOS 27.0.1 / ARM64 |
| Maven | 3.6.0, through the unchanged Maven wrapper |
| Spring Boot / Spring Data JPA | 3.1.7 / 3.1.7 |
| Hibernate ORM | 6.2.17.Final |
| Kotlin / ANTLR | 1.9.22 / 4.13.1 |
| H2 | 2.2.220 |
| Database URL / driver / user | `jdbc:h2:mem:testdb` / `org.h2.Driver` / `sa` |
| Test context | Spring Boot without a web server; transactional Kotlin tests |
| Test engine / reports | JUnit Jupiter, Maven Surefire 3.0.0 |
| Evidence runner | Python standard library; Python 3.14.3 locally |

The original lifecycle command is:

```sh
./mvnw --batch-mode -Dgpg.skip=true -Ddependency-check.skip=true clean verify
```

These two skip flags match inherited test CI: artifact signing and the OWASP
dependency scan are excluded, not tests or coverage checks. Git commit signing
is separate and remains enabled. This is not a vulnerability-scan result.

| Result | Pinned upstream | Imported |
| --- | ---: | ---: |
| Source inventory | 113 | 113 |
| Discovered in Surefire reports | 113 | 113 |
| Executed | 113 | 113 |
| Passed | 113 | 113 |
| Failed assertions | 0 | 0 |
| Errors | 0 | 0 |
| Skipped | 0 | 0 |
| Missing / unexpected / duplicate cases | 0 / 0 / 0 | 0 / 0 / 0 |
| Build exit code | 0 | 0 |

All per-scenario outcomes and resolved dependency trees match. Both inherited
JaCoCo checks pass. Identical aggregate coverage is 2,448/2,857 instructions
(85.68%), 205/246 branches (83.33%), and 315/341 lines (92.38%). These are upstream
aggregate metrics with upstream exclusions, not changed-line coverage or proof of
complete equivalence. No production code changes were introduced in Stage 0.

[results.json](results.json) retains the run metadata, counts, scenario outcomes,
coverage counters, input fingerprints, and raw-evidence fingerprints. Full local
evidence is in `build/baseline-final/`; CI uploads its own `build/baseline/`.

### Findings and coverage gaps

- No pre-existing test failures or skips were observed. Duplicate Kotlin source
  root warnings and generated-parser JavaDoc warnings occur in the unchanged
  build; the logs retain them.
- `SpringSearchApplicationTest.run()` has an empty body. It exercises context
  startup but asserts no query behavior; it remains in the inventory and suite.
- PostgreSQL, pagination/count stability, mandatory consumer scopes, and
  association predicates combined with OR are not established by this suite.
- The test application has no web server, and upstream JaCoCo excludes resolver
  configuration and generated grammar classes. The baseline does not establish
  HTTP argument-binding coverage or a Java-only consumer API.
- Case-insensitive pattern tests do not establish consistent behavior for every
  text operator. The new word-matching policy, literal SQL-metacharacter escaping,
  explicit internal wildcards, field registration, and complexity limits require
  separate acceptance tests in their intended stages.
- Initial sandbox execution could not write the Maven cache. It did not execute
  tests. An initial evidence pass also selected help-plugin 3.4.0, which requires
  Maven 3.6.3; only effective-POM capture failed. Both original suites passed in
  that pass. The runner now pins reporting-only help-plugin 3.2.0, compatible
  with Maven 3.6.0, and the complete evidence pass succeeds. Neither issue was an
  upstream test failure; neither required changing the original build.

## Reproduce and retain evidence

Select JDK 17 in `JAVA_HOME` and put its `bin` directory first on `PATH`. Use
Python 3.12 or newer and a full Git checkout containing the pinned upstream
commit. Maven Central access is needed for uncached dependencies.

```sh
python3 -m unittest discover -s scripts -p 'test_compatibility_baseline.py' -v
python3 scripts/compatibility-baseline.py
```

The default output directory is `build/baseline/`. A nonempty output directory
is rejected to preserve earlier evidence; select a fresh directory for a repeat:

```sh
python3 scripts/compatibility-baseline.py --output build/baseline-repeat
```

Each run retains environment and Maven version logs, source manifests, original
build logs, Surefire XML/text, JaCoCo XML/HTML/execution data, a verbose dependency
tree, the effective POM, per-scenario results, and the combined gate result.
Reporting-only dependency-plugin 3.6.1 and help-plugin 3.2.0 are invoked explicitly;
they do not change project dependencies or lifecycle plugin versions.

A failed build still produces available evidence and a nonzero runner exit code.
Failure categories distinguish test, compilation, infrastructure/dependency,
coverage-check, and other build failures. A missing, replaced, duplicated, or
skipped test; differing dependency tree; changed protected input; missing coverage
report; or failed evidence command also fails the gate. Do not weaken assertions
or remove scenarios to obtain a passing gate.

The pull-request workflow targets `main` and also supports manual execution. It
uses JDK 17, runs both suites, and uploads evidence with `if: always()` for 30 days
(subject to repository retention limits). Download and archive a baseline artifact
before it expires if it will be needed for a later stage. CI has not been run by
the local implementation; the recorded execution above is local evidence.

The inherited publishing workflow has been removed. Upstream publishing metadata
remains in the unchanged POM solely to preserve this comparison; do not invoke
`install`, `deploy`, or inherited release scripts as part of Stage 0.

## Licensing and next gate

The distribution and new contributions use the Apache License 2.0 in `LICENSE`.
Inherited material retains the upstream MIT copyright and permission notice in
`LICENSE.txt`; `NOTICE` identifies that origin and the Stage 0 changes. Source
headers are unchanged. Packaging notices into release artifacts remains a later
release task; nothing is published by this milestone.

Next is Stage 1: Java production code with the original Kotlin tests, grammar,
dependencies, fixtures, and matching behavior held fixed. Keep package/API changes,
the Java test port, framework upgrade, final module split, and improved matching
policies in their separately verifiable stages. Add any missing characterization
tests against both implementations before relying on them as compatibility evidence.
