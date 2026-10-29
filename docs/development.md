# Building, testing and troubleshooting

## Prerequisites and H2 default

Use OpenJDK 21.0.8 or newer in the Java 21 line for Maven/Checkstyle/compilation,
selected through `JAVA_HOME` and `PATH`. The compiler targets Java 17 using
`maven.compiler.release`. The wrapper
supplies Maven 3.10.0. Run `./mvnw --version` to confirm the active JDK; do not use
a different JDK such as 27 to establish this compatibility matrix.

```sh
./mvnw --batch-mode -Dgpg.skip=true clean verify
```

No profile means in-memory H2. Docker is not required. The same command with
`install` instead of `verify` also writes the unpublished snapshot to your local
Maven repository for another local project. Maven creates generated sources,
compiled output, tests, coverage, binary/source/Javadoc JARs under each module’s
`target/`. The root project is the parent/aggregator POM. JPA also attaches a
test-only fixture JAR for the starter tests; it is not a runtime dependency.
No repository scripts or Python environment are needed.

To run tests on Java 17 or 25 while keeping Maven on Java 21, select a forked
test executable (replace the example path with the installed runtime):

```sh
./mvnw -Dgpg.skip=true -Djvm=/path/to/jdk17/bin/java clean verify
```

Checkstyle runs during `validate` before compilation; see the
[Spring Checkstyle guide](checkstyle/README.md) for formatting and compatibility rules.

## Nullability checks

Like Pembana, the build uses `am.ik.maven:nullability-maven-plugin:0.5.1` as a
Maven extension with its `configure` goal, plus JSpecify 1.0.1 annotations.
The plugin's `checking=tests` configuration runs Error Prone 2.50.0 and NullAway
0.14.1 during both production and test compilation; nullability violations fail
the build. No separate command or CI setup is needed. The compiler still emits
Java 17 bytecode, and tests can run on Java 17, 21, or 25.

Use an OpenJDK-based Java 21 distribution such as Temurin, version 21.0.8 or
newer. The plugin enables `-XDaddTypeAnnotationsToSymbol=true` so javac reads
JSpecify type-use annotations from dependencies correctly. Oracle JDK 21 does
not support that flag. CI already uses current Temurin 21.

All handwritten production packages declare `@NullMarked`, including their
nested types. Intentional nulls use `@Nullable`: absent/empty MVC searches and
unsupported legacy operations can still produce no specification or predicate,
scalar converters retain their strategy-specific null behavior, and the `NULL`
query token remains distinct from a Java null reference. Nullable Spring MVC/JPA
and ANTLR callback parameters are declared explicitly. Membership items retain
their original scalar conversion, including a null item becoming the string
`"null"` before conversion. Tests declare their own `@NullMarked` package, covering
fixtures, repositories, nested endpoints and the standalone Java caller. Generated
ANTLR sources remain excluded by the plugin's default generated-source filter;
handwritten production and test code have no unmarked regions or nullability
suppressions.

Fixture nullability follows the original Kotlin declarations, including generated
IDs, optional entity associations and nullable scalar columns, plus the nullable
last name added for Stage 4. Test-only set factories permit nullable elements.
Diagnostic assertions check that optional reason text and problem-detail
properties exist before inspecting their contents. Fixture defaults, database
mappings and original scenario assertions are preserved.

Ensure new handwritten production and test types inherit or declare `@NullMarked`,
and use JSpecify `@Nullable` for intentional nulls. Verify callers and behavior
before changing nullability contracts or implementation. Keep the plugin's
default error severity and explicit marking requirement. Error Prone
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

The subsequent test/fixture migration also passes all 153 tests on the same six
database/runtime combinations with production and test checking enabled. Compiler
probes in an isolated copy reject intentional nullability violations in both
source sets. The 113 original scenario methods and production sources are
unchanged; fixture bytecode instructions match the preceding implementation.
All classes still target Java 17. Native compiler, test and coverage evidence is
retained locally under ignored `build/test-nullability/`. CI runs these checks
through the existing Maven lifecycle and retains failures in its build logs.

## PostgreSQL profile

Start a Docker-compatible engine and run:

```sh
./mvnw --batch-mode -Dgpg.skip=true -Dspring.profiles.active=postgresql clean verify
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
complete 242-method suite and per-module coverage, then a Java caller against the
packaged JAR using that same runtime in a separate JVM. Dependencies, effective
POMs, environment/build logs, Surefire XML, and coverage reports are uploaded
for 30 days, including evidence from failed jobs. Publication uses the separate
manual workflow documented in [Releasing](releasing.md).

The original 113 methods now reside in `PembanaSearchCompatibilityTest`.
The eight prior integration checks, 27 Stage 4 tests and five nullability contract
tests are retained. New module tests add 26 core checks, five JPA model checks and
one resolver-override check. Eight further case-equality regressions verify the
intentional correction of false-flag equality/inequality; the prior Stage 4
characterization is updated and documented in the migration guide. Request-option
coverage adds 39 checks across immutable core options, JPA policy/type handling
and GET/JSON binding while retaining the prior 193 methods. The HTTP Stage 4 method now lives in
`MvcQueryBehaviorTest`; its scenario body is unchanged. Frozen
inventories use the old class name; the identity mapping is in the
[migration guide](migration.md). Preserve method names/queries/assertions rather
than updating historical hashes to make a comparison appear equivalent.

Maven enforces 80% instruction and 75% branch coverage with only generated parser
classes excluded. New/materially changed production code additionally needs 80%
changed-line coverage and meaningful failure-path tests. Surefire output is under
`<module>/target/surefire-reports`; coverage HTML/XML is under
`<module>/target/test-results/coverage/jacoco`. Core runs 32 tests, JPA 184 and
the starter 26. Ten JPA regression methods cover collection mapping styles and
nested enum conversion in both validation modes. All modules enforce the same
coverage gates independently. Distinguish compilation, infrastructure, test,
coverage, documentation, signing and scan failures in findings.

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

Dependency-Check is opt-in through the Maven **`security` profile**. Ordinary
builds, compatibility CI and publication staging do not load or run it, so no
skip flag is needed. Selecting the profile binds one root-only **`aggregate`**
scan to `verify`, covering the reactor's dependencies in one HTML/JSON report.
The aggregate execution is not inherited by child modules. Run from the repository root
without `-N` for the aggregate scan:

```sh
./mvnw -Psecurity -Dgpg.skip=true clean verify
```

Reports are `target/dependency-check-report.html` and
`target/dependency-check-report.json`. Existing test-scope exclusions and the
plugin's default CVSS threshold of **11** are retained: findings are reported,
but do not fail the build solely on severity. Analysis/update errors fail the
build. To enforce an explicit severity threshold for a particular run, supply
`-DfailBuildOnCVSS=7`, for example. A successful report-only run is not a claim
that no vulnerabilities exist; review the report before releasing.

### API key and local cache

The profile accepts **`NVD_API_KEY` directly from the environment**. Alternatively,
it looks up Maven server ID `nvd` in private user settings. The environment value
takes precedence. Never pass the key as `-DnvdApiKey=...` or commit credentials.
For the Maven settings alternative (`~/.m2/settings.xml`):

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

Dependency-Check retains its local database under the Maven local repository by
default (`~/.m2/repository/org/owasp/dependency-check-data/`, with a schema-version
subdirectory). `mvn clean` does not remove it. First-time initialization can take
substantial time even with an API key; subsequent runs reuse it and fetch updates
when needed. Avoid purging the cache during routine builds.

To update data separately and then scan using the refreshed cache:

```sh
./mvnw -N -Psecurity dependency-check:update-only
./mvnw -Psecurity -Dgpg.skip=true -DautoUpdate=false verify
```

Use `autoUpdate=false` only after a successful update or with a known fresh cache;
it is not a permanent freshness policy. Some analyzers may still make network
requests. For repeated local scans, `-DnvdValidForHours=24` instead keeps automatic
updates enabled while extending the NVD check interval from four to 24 hours.
Use `-DdataDirectory=/absolute/path/outside/target` consistently for both commands
if selecting another database directory. Do not use a directory that `clean`
will delete. `-Ddependency-check.skip=true` still skips an explicitly enabled
security scan; `-Dnvd.skip` is not the execution-skip property.

### Nightly/manual security workflow

**Actions → Dependency security scan → Run workflow** starts a scan on demand.
The same workflow runs daily at **10:23 UTC / 00:23 Hawaii Standard Time** on the
default branch. Configure the repository Actions secret **`NVD_API_KEY`** first.
The update step fails with an actionable message if it is absent rather than
starting a slow unauthenticated download. The workflow uses Java 21 and H2 tests;
it does not repeat the six-database/runtime compatibility matrix or publish
artifacts.

Each run restores the complete vulnerability data directory to
`$RUNNER_TEMP/dependency-check-data`, separate from setup-java's Maven artifact
cache. The cache key includes runner OS, the exact Dependency-Check version read
from the POM, a manual cache epoch (`v1`), and the run ID/attempt. Restore prefixes
stay within that OS/plugin-version/epoch; incompatible versions are never used as
a fallback. Bump the epoch in the workflow if a cache must be discarded. GitHub
caches are subject to branch access rules and eviction, so an initial or evicted
cache still requires full initialization.

Runs are serialized. Each run updates the database once with `update-only`, saves
a new immutable cache entry after that update succeeds, then executes the single
aggregate scan with `autoUpdate=false`. Saving before analysis retains refreshed
data even if analysis/build subsequently fails; a failed update is never saved.
A unique key allows refreshed data to persist instead of reusing an immutable
old entry forever. No credentials or settings files belong in this data cache.

HTML/JSON reports, update/scan logs, revision/plugin version and available build
reports are uploaded for 30 days, including on failure. Cache failures or update
failures must not be interpreted as a clean security result. Ordinary CI and
publication staging do not activate this profile; inspect the separate security
workflow/report for the intended release revision. Never upload secret keys or
settings files in reports.

See the upstream [aggregate goal](https://dependency-check.github.io/DependencyCheck/dependency-check-maven/aggregate-mojo.html)
and [database caching guidance](https://dependency-check.github.io/DependencyCheck/data/cacheh2.html).

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
  omit `-Psecurity` for an ordinary build; use the separate security workflow
  for an updated vulnerability report.
- **Artifact GPG failure:** configure artifact signing or use `-Dgpg.skip=true`
  for local verification. Continue signing Git commits.
- **Repository certificate warning:** inherited metadata resolution has produced
  a `jvnet-nexus-releases` certificate warning. Report whether resolution truly
  failed; do not confuse a warning with a test failure or disable TLS checks.
- **Javadoc failure:** fix source documentation errors. Generated ANTLR sources
  are excluded from public API docs, not from compilation or behavior tests.
