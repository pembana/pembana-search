# Search feature acceptance

Verification date: 2026-11-03. The adopted compatibility baseline is
`e870eeb5f73e93ae5ec487787c879fd0d1e5232c`. Production code under test is revision
`88c893c6023d5ad98a1935b6ad6a4be74d3440e9`, with the test, documentation, and CI
additions accompanying this report. This follow-up changes no production API,
matching policy, or dependency version.

## Scope and accepted transition

The [feature handoff](requirements/pembana-search/README.md) is now maintained in
this repository. Its [accepted transition](requirements/pembana-search/compatibility-and-extension-requirements.md#accepted-migration-changes--2026-11-02)
permits the added annotation member, AND-before-OR precedence, strict-validation
and size defaults, consistent inherited case handling, and exact Long conversion.
Those changes require migration from earlier snapshots. They are not evidence of
binary compatibility with a handwritten annotation compiled before `profile()`.

SIMPLE supports root/nested String properties and collection String fields.
Profiles, converters, transformations, custom predicates, optional-to-one paths,
and explicit collection scopes are available through the documented APIs.
Maps, multiple collection steps in one path, and associations after embedded
attributes remain outside the agreed support boundary. Consumer-specific
restrictions and search UI behavior remain application responsibilities.

## Executable checks

| Requirement area | Evidence |
| --- | --- |
| TEXT matching and JDBC parity | `PlainTextJpaTest.jdbcJpaAndMemoryParity` runs 24 inputs in both case modes through memory, JPA, and the runnable `PlainTextJdbcConsumer` sample. |
| Adopted source/binary API | `ApiCompatibilityTest` is compiled unchanged against baseline JARs, run against those JARs, and then run unchanged against candidate JARs. The same source also compiles and runs against the candidate. |
| Profiles and extensions | Existing core profile, case-option, JPA extension, and HTTP profile suites run in the full matrix. |
| Associations, scopes, counts and pages | Existing optional-to-one and collection suites, including the SIMPLE nested-path regressions, run on both databases. |
| Module/build contracts | Reactor checks include nullability, Checkstyle, coverage, source/Javadoc packaging, and Java 17 bytecode targeting. |

The JDBC corpus covers neutral text, distributed words, missing words, whole-value
and repeated stars, SQL/regex punctuation, quotes, backslashes, attempted SQL
syntax as data, nulls, empty strings, newlines, and Latin accented text. It uses
bound values and the same transactional data as JPA. It does not claim universal
Unicode/collation equivalence or implement a general SQL compiler.

The standalone API client covers a handwritten annotation with the adopted
members, record constructors/accessors/equality, sealed query variants, exhaustive
enum switches, parser defaults/precedence, and existing builder/parser/translator
constructors. It is a representative consumer check, not proof of every possible
consumer's behavior. Preserve this fixture's adopted API contract when changing
the library; do not update it merely to conceal a compatibility break.

## Verification results

The full matrix and source/binary results are recorded under ignored
`build/search-acceptance/`. Each matrix directory retains its build log, runtime
version, native Surefire and JaCoCo reports, and standalone packaged Java caller
output. Builds use OpenJDK 21 and target Java 17 bytecode.

| Test runtime | H2 | PostgreSQL 18.1 |
| --- | --- | --- |
| Java 17.0.20 | 312 passed | 312 passed |
| Java 21.0.12.1 | 312 passed | 312 passed |
| Java 25.0.3 | 312 passed | 312 passed |

Each run has zero failures, errors, or skips: 44 core tests, 236 JPA tests, and
32 starter tests. All module coverage gates pass, and all six packaged Java caller
runs pass. Public declarations and JVM descriptors match the adopted baseline
across 129 runtime classes; those classes all target Java 17 (class-file version
61). Runtime JAR license payloads match the repository notices.

The standalone adopted-source/binary checks pass on Java 17.0.20, 21.0.10, and
25.0.3. Baseline and candidate classpaths explicitly exclude cached Pembana™
artifacts, so the comparison cannot silently fall back to an installed snapshot.
The added `api-compatibility` CI job repeats the baseline/source/binary check on
Java 21; the existing matrix job runs the full suite on all six combinations.

## Reproducing and delivery boundaries

Run the reactor as described in [development](development.md), selecting each
runtime and database:

```sh
./mvnw -B -ntp -Dgpg.skip=true -Djvm=/path/to/runtime/bin/java \
  -Dspring.profiles.active=h2 clean verify
```

Repeat with `postgresql` and a Docker-compatible engine. Run
`com.pembana.search.JavaCallerSmokeTest` against the packaged JARs as shown in
`.github/workflows/maven.yml`. The same workflow records the exact baseline,
packages it without installing snapshots, and compiles/runs the unchanged API
consumer against both artifact sets.

Local verification does not mean the new workflow has run on GitHub or that these
artifacts were published. Publication, signing, remote artifact parity, and any
application deployment follow the separate [release process](releasing.md).
Application integration evidence belongs to the consuming application; this
increment does not modify that application's working tree. Historical baseline
reports remain unchanged.
