# PostgreSQL testing with Testcontainers

PostgreSQL tests now use Testcontainers both locally and in GitHub Actions.
The Spring corpus and standalone Java caller read the same
`application-postgresql.properties`, with JDBC URL
`jdbc:tc:postgresql:18.1:///search_tests` and Testcontainers' JDBC driver.
Testcontainers starts PostgreSQL 18.1 on a free host port and cleans up the
container when its last connection closes; its resource reaper also handles JVM
exit. Containers are disposable, with no opt-in reuse or daemon mode enabled.
The tests need no pre-existing PostgreSQL server, fixed port, or connection
environment variables.

`org.testcontainers:testcontainers-postgresql` is test-scoped and managed by
Spring Boot 4.1.1 at version 2.0.5. It brings the Testcontainers JDBC module;
the existing PostgreSQL JDBC driver remains test-scoped. H2 stays in memory with
its original configuration. Production sources, framework versions, query
grammar, original scenarios, assertions, and fixtures are unchanged. There are
no new Java classes; existing classes retain their 2026 Apache 2 headers.

## Run locally or in CI

With JDK 17, 21, or 25 selected in `JAVA_HOME` and `PATH`, H2 needs only Maven:

```sh
./mvnw --batch-mode -Dgpg.skip=true -Ddependency-check.skip=true -Dspring.profiles.active=h2 clean verify
```

For PostgreSQL, start a Docker-compatible engine, then run:

```sh
./mvnw --batch-mode -Dgpg.skip=true -Ddependency-check.skip=true -Dspring.profiles.active=postgresql clean verify
```

The first PostgreSQL run downloads the database and resource-reaper images.
Missing Docker or a failed container startup fails the PostgreSQL job; it never
silently switches to H2 or skips database tests. H2 remains the default if no
profile is selected. `POSTGRES_URL`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` are
no longer used by the test harness. Any normal Testcontainers Docker-runtime
configuration applies to both Maven and the packaged-JAR caller.

GitHub Actions retains the Java 17/21/25 × H2/PostgreSQL matrix on pull requests
to `main` and manual dispatch. Ubuntu runners supply Docker; Testcontainers
owns PostgreSQL startup using the same profile as local builds. The service
container and its fixed port/credentials have been removed. Native Maven logs,
Surefire reports, dependency versions, effective POMs, and coverage are still
uploaded even when checks fail, with 30-day retention.

## Verification

All six clean verification builds pass locally on 2026-10-28:

| Java | H2 2.4.240 | Testcontainers PostgreSQL 18.1 |
| --- | --- | --- |
| 17.0.20, BellSoft | 121 passed | 121 passed |
| 21.0.10, Temurin | 121 passed | 121 passed |
| 25.0.3, Temurin | 121 passed | 121 passed |

Each run discovers and executes all **113 original scenarios**, with matching
outcomes against the preceding Stage 3 verification, plus eight supplemental
checks. There are **zero failures, errors, or skips**. The packaged-JAR Java
caller also passes in a fresh JVM for every combination; its PostgreSQL runs
launch their own disposable database using the shared profile. Logs confirm
Testcontainers 2.0.5 and PostgreSQL 18.1 startup, and H2 runs do not start containers.

Production coverage remains **279/325 lines (85.85%)**, **1,750/2,108 instructions
(83.02%)**, and **218/283 branches (77.03%)**. The existing coverage gates pass
unchanged. Bytecode targets Java 17 under all three JDKs. Existing scenario
coverage limits, including the empty `run()` smoke test, remain as documented
in the earlier reports.

[results.json](results.json) records counts, outcomes, environment details,
unchanged-input fingerprints, and raw-evidence hashes. Full local evidence is
under ignored `build/testcontainers/`. Local verification uses macOS ARM64 with
OrbStack Docker 29.4.0; the GitHub workflow is configured but has not run for this
commit. Maven artifact signing and Dependency-Check are skipped by the commands
above; Git commit signing is separate. No artifacts are published.
