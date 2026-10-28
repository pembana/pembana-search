# Building, testing and troubleshooting

## Prerequisites and H2 default

Use JDK 17, 21, or 25 and select it through `JAVA_HOME` and `PATH`. The wrapper
supplies Maven 3.10.0. Run `./mvnw --version` to confirm the active JDK; do not use
a different JDK such as 27 to establish this compatibility matrix.

```sh
./mvnw --batch-mode -Dgpg.skip=true -Ddependency-check.skip=true clean verify
```

No profile means in-memory H2. Docker is not required. The same command with
`install` instead of `verify` also writes the unpublished snapshot to your local
Maven repository for another local project. Maven creates generated sources,
compiled output, tests, coverage, binary/source/Javadoc JARs under `target/`.
No repository scripts or Python environment are needed.

## PostgreSQL profile

Start a Docker-compatible engine and run:

```sh
./mvnw --batch-mode -Dgpg.skip=true -Ddependency-check.skip=true -Dspring.profiles.active=postgresql clean verify
```

This is a **Spring profile**, not Maven `-Ppostgresql`. Its JDBC URL is
`jdbc:tc:postgresql:18.1:///search_tests`. Testcontainers selects a free host port,
starts a disposable PostgreSQL 18.1 instance, and cleans up connections/containers.
The standalone Java caller reads the same profile resource. No external database,
`POSTGRES_URL`, `POSTGRES_USER`, or `POSTGRES_PASSWORD` is needed. Missing Docker
fails the PostgreSQL run; tests are not skipped or redirected to H2.

## CI and test identities

Pushes to any branch or tag, pull requests to `main`, and manual workflow dispatch run Java 17/21/25 ×
H2/PostgreSQL. Every job runs the complete 148-method suite and coverage, then a
Java caller against the packaged JAR in a separate JVM. Dependencies, effective
POMs, environment/build logs, Surefire XML, and coverage reports are uploaded
for 30 days, including evidence from failed jobs. No deployment is configured.

The original 113 methods now reside in `PembanaSearchCompatibilityTest`.
The eight prior integration checks and 27 Stage 4 tests are supplemental. Frozen
inventories use the old class name; the identity mapping is in the
[migration guide](migration.md). Preserve method names/queries/assertions rather
than updating historical hashes to make a comparison appear equivalent.

Maven enforces 80% instruction and 75% branch coverage with only generated parser
classes excluded. New/materially changed production code additionally needs 80%
changed-line coverage and meaningful failure-path tests. Surefire output is under
`target/surefire-reports`; coverage HTML/XML is under
`target/test-results/coverage/jacoco`. Distinguish compilation, infrastructure,
test, coverage, documentation, signing and scan failures in findings.

## Commit signing versus artifact signing

Use repository-configured Git signing:

```sh
git config --local commit.gpgsign true
git commit -S -m "docs: describe the change"
git verify-commit HEAD
```

`-Dgpg.skip=true` skips Maven artifact signatures only. It does not disable Git
commit signing. Release artifact keys/configuration remain a separate gate.
Do not rewrite the imported history.

## Dependency-Check and NVD

The POM's Dependency-Check configuration looks up Maven server ID `nvd`. Keep
credentials outside the repository. For example, put this in user settings
(`~/.m2/settings.xml`) and supply `NVD_API_KEY` in the environment:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
    <servers>
        <server>
            <id>nvd</id>
            <password>${env.NVD_API_KEY}</password>
        </server>
    </servers>
</settings>
```

Run verification without `-Ddependency-check.skip=true` to include the scan.
The key improves NVD access but first-time data initialization can still take
substantial time. `-Dnvd.skip` is not this build's execution-skip property.
Compatibility commands and CI deliberately skip the network scan; passing those
checks is not evidence of a clean vulnerability scan. Never put keys or settings
files into commits, logs, or uploaded artifacts.

## Troubleshooting

- **Wrong Java:** confirm `./mvnw --version` and fix both `JAVA_HOME` and `PATH`.
- **Docker unavailable:** use default H2 for local work or start/configure a
  supported Docker-compatible engine before selecting `postgresql`.
- **Unknown field/type:** query keys are Java entity attributes. Enable strict
  validation for structured rejections, and consult the query reference.
- **Missing resolver:** ensure MVC is supplied and the auto-configuration has not
  been excluded. Imports use `com.pembana.search.annotation.SearchSpec`; no old
  `com.sipios` annotation should remain on endpoints.
- **Missing search:** the resolver returns null for an absent/empty parameter;
  branch explicitly before repository methods that reject null Specifications.
- **NVD download/rate failure:** verify the `nvd` server ID/environment key, or
  skip Dependency-Check explicitly for a compatibility-only run.
- **Artifact GPG failure:** configure artifact signing or use `-Dgpg.skip=true`
  for local verification. Continue signing Git commits.
- **Repository certificate warning:** inherited metadata resolution has produced
  a `jvnet-nexus-releases` certificate warning. Report whether resolution truly
  failed; do not confuse a warning with a test failure or disable TLS checks.
- **Javadoc failure:** fix source documentation errors. Generated ANTLR sources
  are excluded from public API docs, not from compilation or behavior tests.
