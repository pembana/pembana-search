# Stage 3: Spring Boot 4 and database compatibility

Historical report for revision `15eec77`: native PostgreSQL and the CI service
container described here have since been replaced by
[Testcontainers testing](../testcontainers/README.md). Use that report's commands
with the current checkout.

Status: **passed locally on 2026-10-28**. Spring Boot 4.1.1 passes the unchanged
113-scenario corpus on Java 17, 21, and 25 against both H2 and PostgreSQL.
The pre-upgrade comparison is signed revision
`9d6494f0da50d2522e00bf7f326b9ba0150174aa` (Stage 2).

## Implementation and dependencies

The Spring Boot parent moves from 3.1.7 to 4.1.1. Its modular Web MVC and JPA test
starters replace their former equivalents. Boot now manages the compiler plugin
and the H2, Jackson, and SnakeYAML versions instead of the inherited overrides.
The Maven wrapper remains 3.10.0; ANTLR remains 4.13.1. JaCoCo moves to 0.8.14 for
Java 25 instrumentation, retaining the existing exclusions and coverage gates.

| Component | Resolved version |
| --- | --- |
| Spring Boot | 4.1.1 |
| Hibernate ORM | 7.4.5.Final |
| H2 | 2.4.240 |
| PostgreSQL server tested | 18.1 |
| PostgreSQL JDBC driver (test scope) | 42.7.13 |
| Jackson JSON support | 3.1.5 |
| Jackson 2 date parser | 2.21.5 |
| SnakeYAML | 2.6 |

`DateStrategy` still uses Jackson 2 `StdDateFormat`. Jackson 2 databind therefore
becomes an explicit compile/runtime dependency: Boot 4's Jackson 3 Web MVC stack
no longer supplies that class to consumers. This preserves date parsing without
enabling Jackson 2 MVC auto-configuration or changing Boot's JSON defaults.

The resolver configuration uses Boot's `@AutoConfiguration` and
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
The obsolete `spring.factories` entry is removed. Conditions restrict registration
to servlet applications with MVC available. Four new checks cover imports
discovery, servlet resolver registration, direct JPA startup, and missing MVC
classes. A fifth check inspects JDBC metadata to ensure each database job actually
connects to its selected engine. Both new Java classes have 2026 Apache 2 headers.

The original H2 configuration, all original tests and fixtures, grammar, parser,
matching strategies, resolver logic, and public APIs are unchanged. Only
`ResolverConf` and its registration resource change in production. The existing
standalone Java caller selects the matrix database without changing its query or
assertions. No Kotlin sources or resolved Kotlin dependencies are introduced.

## Verification

The pre-upgrade Java 17/H2 build passes all 116 tests (113 original plus three
supplemental). Each upgraded build has eight supplemental checks: the existing
three and the five new checks above.

| JDK | Database | Original discovered / executed / passed | Total passed | Failures / errors / skips |
| --- | --- | --- | ---: | --- |
| 17.0.20, BellSoft | H2 2.4.240 | 113 / 113 / 113 | 121 | 0 / 0 / 0 |
| 17.0.20, BellSoft | PostgreSQL 18.1 | 113 / 113 / 113 | 121 | 0 / 0 / 0 |
| 21.0.10, Temurin | H2 2.4.240 | 113 / 113 / 113 | 121 | 0 / 0 / 0 |
| 21.0.10, Temurin | PostgreSQL 18.1 | 113 / 113 / 113 | 121 | 0 / 0 / 0 |
| 25.0.3, Temurin | H2 2.4.240 | 113 / 113 / 113 | 121 | 0 / 0 / 0 |
| 25.0.3, Temurin | PostgreSQL 18.1 | 113 / 113 / 113 | 121 | 0 / 0 / 0 |

Surefire identities and outcomes for every original method match the pre-upgrade
run and the [canonical inventory](../baseline/scenarios.json), with no missing or
unexpected original methods. The empty `run()` method remains a context smoke
test without a behavioral assertion. The earlier reports' coverage gaps remain;
passing the corpus establishes equivalence for those scenarios, not every
possible Hibernate or database behavior.

All six clean verification builds, standalone packaged-JAR Java caller runs,
dependency reports, and effective-POM reports exit successfully. Production
bytecode targets Java 17 (major version 61) under every JDK. Coverage remains
**279/325 lines (85.85%)**, **1,750/2,108 instructions (83.02%)**, and
**218/283 branches (77.03%)**. The inherited instruction 80% and branch 75%
gates pass with only generated grammar classes excluded.

[results.json](results.json) records per-run counts, supplemental method outcomes,
coverage, exit codes, environment details, dependency versions, unchanged input
hashes, and evidence hashes. Full local logs, Surefire XML, dependency trees,
effective POMs, and HTML/XML coverage reports remain in `build/stage3/` (ignored
build output). A temporary isolated native PostgreSQL 18.1 cluster was used on
loopback port 55439 and stopped after testing.

Build output includes a pre-existing certificate-expiry warning from Maven's
inherited `jvnet-nexus-releases` metadata repository. It causes no compilation,
test, infrastructure, or coverage failure in these runs. Dependency-Check and
Maven artifact signing are explicitly skipped for compatibility verification;
this result does not establish a vulnerability-scan result. Git commits remain
signed separately.

## Reproduce and CI

Select each of JDK 17, 21, and 25 with `JAVA_HOME` and `PATH`, then run:

```sh
./mvnw --batch-mode -Dgpg.skip=true -Ddependency-check.skip=true -Dspring.profiles.active=h2 clean verify
```

For PostgreSQL, provide a disposable PostgreSQL 18.1 database. Both test harnesses
create and drop their mapped tables, so use a dedicated test database:

```sh
export POSTGRES_URL=jdbc:postgresql://localhost:5432/search_tests
export POSTGRES_USER=search_tests
export POSTGRES_PASSWORD=search_tests
./mvnw --batch-mode -Dgpg.skip=true -Ddependency-check.skip=true -Dspring.profiles.active=postgresql clean verify
```

The PostgreSQL profile overrides only the connection and schema lifecycle; the
same original queries, fixtures, and assertions execute on both engines. H2
remains the default when no profile is supplied.

GitHub Actions runs the six combinations on pull requests to `main` and manual
dispatch, using Temurin 17/21/25 and a PostgreSQL 18.1 service container. It also
runs the packaged Java caller and retains native Maven evidence for 30 days,
including reports from failed checks. No custom build scripts or Python setup
are required. GitHub CI is configured but has not been executed for this local
change; local verification used macOS ARM64 and the JDK vendors listed above.

## Compatibility scope and next gate

The supported framework baseline for this milestone is Spring Boot 4.1.1 with
Java 17, 21, or 25 and the tested H2/PostgreSQL versions above. Earlier stage
reports describe historical comparisons rather than a continuing Boot 3 support
promise. Other JDK/framework/database combinations are not verified here.

Existing packages, Maven coordinates, module boundaries, and matching policies
remain unchanged. No artifact is installed, deployed, or published. The next
change can address improved APIs and matching policies against this baseline;
namespace/module restructuring and release configuration need their own gate.
