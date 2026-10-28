# Stage 2: Java tests and fixtures

Historical report: the pinned Kotlin comparison described below was the Stage 2
CI gate. Stage 3 replaces it with the [framework/database matrix](../stage3/README.md).

Status: **passed locally on 2026-10-28**. Production code, resources, grammar,
framework integration, query behavior, and coverage thresholds are unchanged.
This completes the test-language port; the Spring Boot upgrade remains Stage 3.

## Frozen implementation and comparison

The transition starts from committed revision
`b6b56e1da5e5897eadab20b957c206acae1a3710`. This includes the existing Java production
implementation, the original Kotlin corpus, and the separately committed Maven
Wrapper 3.10.0 / Dependency-Check 13.0.0 updates. Those updates are retained;
Stage 2 does not upgrade or downgrade the remaining dependency/plugin versions.

Two isolated builds avoid duplicate fixture class names during the transition:

1. Run the retained Kotlin tests and fixtures from the pinned revision against
   its frozen Java production implementation.
2. Run the Java candidate against identical production sources, retaining the
   Kotlin dependencies/plugin configuration in the candidate POM during this gate.
   The candidate replaces only the test sources, so the unused Kotlin compiler
   finds no Kotlin sources. Both original and translated corpora remain available
   in separate checkouts until verification completes.
3. After all 113 Java scenarios pass with equivalent outcomes, copy the Java
   tests into the working tree, remove Kotlin tooling, and run a clean final build.

All three builds used BellSoft OpenJDK 17.0.20+10-LTS, Maven 3.10.0, Spring Boot
3.1.7, H2 2.2.220, and the unchanged test database configuration. The builds have
**61 byte-identical production class files**, including generated parser classes.
Production source/resource SHA-256 fingerprints also match. No change to
`src/main` was needed.

## Scenario and fixture mapping

[scenarios.json](scenarios.json) maps each original Kotlin test to one Java method
with the **same class and method name**. There is no consolidation or
parameterization that could hide missing cases. For each of the 113 methods,
the audit checks matching assertion types/counts and string/numeric input
literals, including query punctuation, special characters, case flags, expected
names/counts, and exception types. Per-method source fingerprints retain the
comparison inputs. Runtime Surefire reports verify matching identities/outcomes.

Mechanical language adaptations are:

- Kotlin named fixture arguments become explicit fluent field overrides. Java
  fixture defaults use the same values and date/time/UUID generators; nullable
  and primitive types, field names, table/column annotations, generated IDs,
  association mappings, cascade behavior, and orphan removal are preserved.
  Explicit overrides are applied after Java fixture construction.
- Java fields replace Kotlin test-property access. The user fixture retains
  data-class value equality/hash behavior; the book fixture retains Hibernate
  proxy-aware identity equality/hash behavior. Unused Kotlin-generated data-class
  convenience methods are unnecessary in the Java-only test harness.
- Kotlin string interpolation becomes Java concatenation. Kotlin structural
  equality becomes Java value equality, and collection transformations become
  streams. The test-only `setOf` factory preserves null/duplicate handling rather
  than adopting `Set.of` restrictions.
- A plain Java annotation factory replaces the Kotlin annotation helper, with
  annotation equality/hash behavior and defensive array copies. No Kotlin
  constructor reflection or runtime is required.
- Java exception assertions use lambdas and methods declare checked exceptions
  where permitted by JUnit. The two otherwise discarded property reads are
  captured in unused Java locals; repository operations remain unchanged.

The empty upstream `run()` method remains an empty context smoke test. The
repository's existing `Long` ID declaration for the integer user fixture is
preserved rather than corrected during the language port. No original queries,
negative cases, asserted results, or fixture values were intentionally changed.

All **nine new Java test source files** carry 2026 Apache License 2.0 headers and
retain upstream MIT attribution. The Stage 1 Java smoke/resolver checks remain
unchanged. Kotlin source files, the Kotlin test helper, four Kotlin-related test
dependencies, the Kotlin compiler/Spring/JPA plugins, the explicit Kotlin version
property, and the ktlint Ant integration have been removed from the final build.

## Verification

| Result | Retained Kotlin | Java candidate | Java-only final |
| --- | ---: | ---: | ---: |
| Original scenarios discovered/executed | 113 / 113 | 113 / 113 | 113 / 113 |
| Original scenarios passed | 113 | 113 | 113 |
| Original failures / errors / skips | 0 / 0 / 0 | 0 / 0 / 0 | 0 / 0 / 0 |
| Missing / unexpected original methods | 0 / 0 | 0 / 0 | 0 / 0 |
| Supplementary Stage 1 Java checks passed | 3 | 3 | 3 |
| Total tests passed | 116 | 116 | 116 |
| Full verification build exit code | 0 | 0 | 0 |

Production coverage is identical in all three builds: **279/325 lines (85.85%)**,
**1,750/2,108 instructions (83.02%)**, and **218/283 branches (77.03%)**. Both
inherited coverage gates pass, with only generated parser sources excluded.
The test port neither adds production coverage nor weakens its checks; the
coverage gaps documented in the earlier stages remain.

Final checks also confirm:

- Handwritten production and test sources are Java; no `.kt`/`.kts` sources remain.
- The resolved test dependency graph and complete test classpath contain no
  Kotlin or Jackson Kotlin dependency. The JAR's embedded POM declares none.
  Inherited Spring Boot BOM metadata may still manage optional Kotlin artifacts;
  that does not make them required dependencies.
- Production and test bytecode have no Kotlin references. A fresh JVM runs the
  existing Java caller against the packaged JAR and the full test classpath,
  without filtering out Kotlin dependencies because none are present.
- The Maven wrapper, database/test resources, production inputs, existing Stage 1
  Java checks, and original baseline inventory are unchanged.

[results.json](results.json) retains counts, outcomes, production source/class
fingerprints, new test source hashes, environment details, and evidence hashes.
Raw logs, the retained Kotlin/candidate source snapshots, and copied Surefire /
JaCoCo reports are under `build/stage2/`. The final build reports remain under
`target/`. The baseline build emits an existing expired-certificate repository
metadata warning, but succeeds; no test skips or weakened assertions are used
to bypass it.

## Reproduce and next gate

Select JDK 17, then run the Java-only build:

```sh
./mvnw --batch-mode -Dgpg.skip=true -Ddependency-check.skip=true clean verify
```

Run the same command in a checkout of the pinned revision above for the retained
Kotlin comparison. The GitHub Actions matrix now pairs that pinned Kotlin test
revision with the current Java test revision, and uploads native Maven evidence
even on failure. Cross-run review uses the scenario mapping and Surefire reports;
there is no custom comparison script or Python setup. GitHub CI has not been run
as part of this local implementation.

Artifact signing and vulnerability scanning remain skipped for this compatibility
verification command; tests and coverage are enabled. Git commit signing is
separate. No artifacts are installed, deployed, or published.

Next is Stage 3: modernize framework integration independently, retaining this
Java corpus, adding PostgreSQL coverage alongside H2, and validating the declared
Java/Spring compatibility matrix before changing matching behavior.
