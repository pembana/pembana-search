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
The recorded baseline compared SHA-256 manifests of those inputs before and
after each build. The committed inventory identifies all 113 tests by fully
qualified class and method name. Compare subsequent Surefire reports against
that inventory when reviewing compatibility; test counts alone are insufficient.

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
| Original evidence capture | Python 3.14.3; subsequently replaced by direct Maven CI steps |

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
evidence is in `build/baseline-final/`. These files are historical evidence from
the initial capture, including the fingerprint of the now-removed Python runner.
They are retained unchanged. Current CI uploads native Maven reports and logs
separately for the pinned upstream and imported revisions.

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
  that pass. Reporting-only help-plugin 3.2.0 was selected, compatible
  with Maven 3.6.0, and the complete evidence pass succeeds. Neither issue was an
  upstream test failure; neither required changing the original build.

## Reproduce and retain evidence

Select JDK 17 in `JAVA_HOME` and put its `bin` directory first on `PATH`.
Maven Central access is needed for uncached dependencies. Run the unchanged build
directly in this checkout:

```sh
./mvnw --batch-mode -Dgpg.skip=true -Ddependency-check.skip=true clean verify
```

For a comparison, run the same command in a separate checkout of the pinned
upstream revision. The GitHub Actions workflow performs these builds as two
matrix jobs using the same Ubuntu image and JDK 17 distribution. Failure of one
job does not cancel the other. Each job records the actual revision and Maven/JDK
versions, and captures the resolved dependency tree and effective POM:

```sh
mkdir -p build/baseline
./mvnw --batch-mode org.apache.maven.plugins:maven-dependency-plugin:3.6.1:tree -Dverbose -DoutputFile=build/baseline/dependencies.txt
./mvnw --batch-mode org.apache.maven.plugins:maven-help-plugin:3.2.0:effective-pom -Doutput=build/baseline/effective-pom.xml
```

Each CI job retains environment and Maven version logs, the original build log,
Surefire XML/text, JaCoCo XML/HTML/execution data, the dependency tree, and the
effective POM. The reporting-only plugin invocations do not change project
dependencies or lifecycle plugin versions. Explicit Bash execution preserves
Maven failure statuses through the log-capture pipelines.

Maven enforces its existing test and coverage checks. Comparing scenario identities,
outcomes, dependencies, and protected source inputs is a review task; CI no longer
provides a custom comparison gate or generated JSON summaries. Review failures in
the native build and Surefire reports, distinguishing test failures from compiler,
infrastructure, and coverage-check failures. Preserve findings rather than
weakening assertions or removing scenarios.

The pull-request workflow targets `main` and also supports manual execution. It
uses JDK 17, runs both suites, and uploads each job's evidence with `if: always()` for 30 days
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
