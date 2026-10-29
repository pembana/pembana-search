# Building, testing and troubleshooting

## Prerequisites and H2 default

Use OpenJDK 21.0.8 or newer in the Java 21 line for Maven/Checkstyle/compilation,
selected through `JAVA_HOME` and `PATH`. The compiler targets Java 17 using
`maven.compiler.release`. The wrapper
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

To run tests on Java 17 or 25 while keeping Maven on Java 21, select a forked
test executable (replace the example path with the installed runtime):

```sh
./mvnw -Dgpg.skip=true -Ddependency-check.skip=true -Djvm=/path/to/jdk17/bin/java clean verify
```

Checkstyle runs during `validate` before compilation; see the
[Spring Checkstyle guide](checkstyle/README.md) for formatting and compatibility rules.

## Nullability checks

Like Pembana, the build uses `am.ik.maven:nullability-maven-plugin:0.5.1` as a
Maven extension with its `configure` goal, plus JSpecify 1.0.1 annotations.
Compilation automatically runs Error Prone 2.50.0 and NullAway 0.14.1; nullability
violations fail the build. No separate command or CI setup is needed. The
compiler still emits Java 17 bytecode, and tests can run on Java 17, 21, or 25.

Use an OpenJDK-based Java 21 distribution such as Temurin, version 21.0.8 or
newer. The plugin enables `-XDaddTypeAnnotationsToSymbol=true` so javac reads
JSpecify type-use annotations from dependencies correctly. Oracle JDK 21 does
not support that flag. CI already uses current Temurin 21.

All five handwritten production packages declare `@NullMarked`, including their
nested types. Intentional nulls use `@Nullable`: absent/empty MVC searches and
unsupported legacy operations can still produce no specification or predicate,
scalar converters retain their strategy-specific null behavior, and the `NULL`
query token remains distinct from a Java null reference. Nullable Spring MVC/JPA
and ANTLR callback parameters are declared explicitly. Membership items retain
their original scalar conversion, including a null item becoming the string
`"null"` before conversion. Generated ANTLR sources and test sources are excluded
from nullability checking by the plugin defaults; handwritten production code
has no unmarked packages or nullability suppressions.

Add `@NullMarked` to new handwritten production types and use JSpecify
`@Nullable` for intentional nulls. Verify callers and behavior before changing
nullability contracts or implementation. Keep the
plugin's default error severity and explicit marking requirement. Error Prone
and NullAway remain compiler tooling; only JSpecify is a project dependency.

The [plugin documentation](https://github.com/making/nullability-maven-plugin/tree/0.5.1)
describes configuration and compiler requirements. `-Dnullability.skip=true`
disables the extension for diagnosis; normal verification and CI must run it.

The full production migration was verified on H2 and Testcontainers PostgreSQL
with Java 17, 21 and 25 test runtimes, building with Temurin 21.0.10: all 153 tests
and packaged Java caller checks passed in every combination, with zero failures,
errors, skips or Checkstyle violations. Coverage and strict Javadoc gates passed.
The five null-contract regressions also passed against the preceding revision
`9771bd6` with only those tests selected and the full-suite coverage gate skipped.
JVM field/method descriptors are unchanged and all compiled classes target Java
17 (major version 61); nullability annotations and the explicit nullable wildcard
bound update type metadata for callers. Grammar, original tests, fixtures,
dependency versions and matching rules remain unchanged. Native verification
evidence is retained locally under ignored `build/nullmarked/`.

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

Pushes to any branch or tag, pull requests to `main`, and manual workflow dispatch
run Java 17/21/25 runtimes × H2/PostgreSQL, always building on JDK 21. Surefire's
`jvm` option selects the test runtime independently of Maven. Every job runs the
complete 153-method suite and coverage, then a Java caller against the packaged
JAR using that same runtime in a separate JVM. Dependencies, effective
POMs, environment/build logs, Surefire XML, and coverage reports are uploaded
for 30 days, including evidence from failed jobs. No deployment is configured.

The original 113 methods now reside in `PembanaSearchCompatibilityTest`.
The eight prior integration checks, 27 Stage 4 tests and five nullability contract
tests are supplemental. Frozen
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

- **Wrong build Java:** confirm `./mvnw --version` uses JDK 21 and fix both
  `JAVA_HOME` and `PATH`. A Java 17 test runtime is selected with `-Djvm`, not by
  running Maven's Java-21-only Checkstyle engine on JDK 17.
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
