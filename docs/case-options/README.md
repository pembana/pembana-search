# Global and per-property case options verification

This milestone adds explicit matching choices without changing the default
`@SearchSpec(caseSensitiveFlag=true)` or the behavior of callers that omit options.
The baseline revision is `163b6a7` (the earlier string equality/inequality fix).
The version remains `1.0.0-SNAPSHOT`; no artifacts are published.

## Implemented contract

`SearchOptions` is an immutable, framework-independent core value separate from
query structure. Effective policy is **property → request-wide → annotation**;
`inherit` proceeds to the next level. The exact HTTP values are `sensitive`,
`insensitive`, and `inherit`.

GET/form binding accepts `caseSensitivity` and
`fieldCaseSensitivity[property]`. JSON uses the same global value and a
`fieldCaseSensitivity` object alongside `search`. JSON binding requires the new
`@SearchRequestBody` marker alongside `@SearchSpec`: existing POST controllers
can retain their own business `@RequestBody` and query/form search binding.
The configured Jackson 3 mapper's constraints are retained, with duplicate-key
and trailing-document rejection enabled.

Malformed options produce structured errors. Property keys must occur in the
query, and explicit property overrides require String attributes when JPA
constructs the predicate. The request-wide choice ignores non-String attributes.
Field restrictions and mandatory authorization scopes continue to apply.

Explicit choices cover string equality/inequality, patterns, membership, ordering,
and range expansion. Inherited choices retain the old membership and less-than
quirks. See the [complete contract and examples](../reference/case-sensitivity.md)
for null behavior, binding rules, database collation limits and suggested form UX.

## Acceptance evidence

[Scenario inventory](scenarios.json) compares all **193 existing test method
bodies** against the baseline after removing comments/whitespace outside literals.
All match. It records each retained class/method and each of the **39 new tests**.
Existing fixtures, database resources and ANTLR grammar are byte-for-byte unchanged.
The inherited empty `run()` method remains present and supplies no behavioral
assertion. Production behavior outside the explicit options contract is covered
by the unchanged corpus.

| Database | Runtime | Discovered | Executed | Failed | Errored | Skipped | Packaged Java caller |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| H2 | Java 17 | 232 | 232 | 0 | 0 | 0 | Pass |
| PostgreSQL | Java 17 | 232 | 232 | 0 | 0 | 0 | Pass |
| H2 | Java 21 | 232 | 232 | 0 | 0 | 0 | Pass |
| PostgreSQL | Java 21 | 232 | 232 | 0 | 0 | 0 | Pass |
| H2 | Java 25 | 232 | 232 | 0 | 0 | 0 | Pass |
| PostgreSQL | Java 25 | 232 | 232 | 0 | 0 | 0 | Pass |

The per-run split is core **32**, JPA **174**, starter **26**. Maven runs on
Temurin 21.0.10 and compiles with release 17. Surefire and the packaged caller use
Liberica 17.0.20, Temurin 21.0.10 or Temurin 25.0.3 as selected. All compiled main
and test classes have class-file major version 61. PostgreSQL uses Testcontainers;
H2 remains the default local database and requires no Docker.

New tests cover precedence, inheritance, repeated properties and Boolean grouping,
dotted paths, patterns, membership, ordering/ranges, nulls, non-text validation,
unknown/unused properties, scopes, programmatic equivalence, GET aliases, JSON
media types, duplicates/types/trailing documents, application mapper constraints,
and legacy POST payload compatibility.

All six `clean verify` runs passed compilation, test execution, per-module
JaCoCo gates, Spring formatting/Checkstyle, production/test nullability checking,
and Javadoc. Final PostgreSQL/Java 25 module coverage:

| Module | Instructions | Branches |
| --- | ---: | ---: |
| Core | 99.4% | 94.0% |
| Data JPA | 89.5% | 83.0% |
| Spring Boot starter | 95.7% | 83.3% |

Changed executable production lines have **188/195 coverage (96.4%)**, exceeding
the 80% requirement. [Machine-readable results](results.json) preserve each run's
module counts, runtime versions, coverage counters, Checkstyle results and
Surefire report hashes. No test, compilation, infrastructure or coverage failures
remain in the final matrix.

All ten packaged JARs retain matching Apache license and upstream MIT notice
payloads. The core source JAR retains the unchanged grammar. Runtime classes are
unique across modules, and no old upstream packages or Kotlin sources are packaged.
Core retains its framework/persistence independence. Framework and database
versions remain fixed; the starter now explicitly declares its already-managed,
already-resolved Jackson 3 databind dependency for request binding.

## Reproduction and retained reports

Run from the repository root with JDK 21 as `JAVA_HOME`, selecting a runtime:

```sh
./mvnw --batch-mode -Dgpg.skip=true -Ddependency-check.skip=true \
  -Djvm=/path/to/jdk-17/bin/java clean verify

./mvnw --batch-mode -Dgpg.skip=true -Ddependency-check.skip=true \
  -Djvm=/path/to/jdk-17/bin/java -Dspring.profiles.active=postgresql clean verify
```

Repeat with JDK 21 and 25 runtime paths. The existing Maven CI workflow already
runs this six-combination matrix on pushes and pull requests to `main`, preserving
native reports even on failure. No GitHub Actions execution is claimed by this
local report.

Local raw evidence is retained under ignored
`build/case-options/matrix/java{17,21,25}-{h2,postgresql}/`: complete build and
packaged-caller logs plus each module's Surefire XML/text reports, coverage
XML/HTML, Checkstyle XML and resolved dependency tree. This change adds no custom
build scripts. Artifact GPG signing and the network vulnerability scan were
skipped for compatibility runs; Git commit signing is verified separately.
