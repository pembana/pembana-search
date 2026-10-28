# Stage 1: Java production code, original Kotlin tests

This is the historical Stage 1 record. The [Stage 2 report](../stage2/README.md)
documents the subsequent Java test port and removal of Kotlin tooling.

Status: **passed locally on 2026-10-28** with JDK 17. No framework upgrade,
package rename, module split, new matching policy, or artifact publication is part
of this change. Stage 0 evidence remains in [the baseline report](../baseline/README.md).

## What changed

All 25 handwritten production Kotlin source files have been translated to Java.
The grammar, ANTLR version and generation settings, production resources, test
database settings, original fixtures, dependency coordinates, and dependency/plugin
versions remain fixed. Maven's normal Java compilation builds production first;
the Kotlin plugin now runs only for retained tests, before Java test compilation.
All new Java source files and the test helper carry standard Apache License 2.0
headers dated 2026. Translated production files additionally retain explicit
upstream MIT attribution referring to `LICENSE.txt` and `NOTICE`.

`jackson-module-kotlin`, `kotlin-reflect`, and `kotlin-stdlib-jdk8` move from compile
to test scope. Their versions are unchanged, as are all resolved dependency
versions. They remain available to the original Kotlin test application but are
not transitive production dependencies of the Java library. The Kotlin compiler
and its Spring/JPA plugins remain solely for that test harness.

Compatibility details:

- Packages, builder construction/chaining, annotation names/defaults, operations,
  strategy classes, and query behavior are retained. Existing text equality,
  case-folding asymmetries, outer-asterisk handling, reflection-based collection
  detection, and enum lookup behavior have not been corrected in this stage.
- Public strategy conversion/factory methods accept Java `Class<?>` instead of
  Kotlin `KClass`. The checked conversion/reflection failures are declared as Java
  exceptions. This is a language-boundary adaptation, not a promise of binary
  compatibility for applications calling Kotlin-specific strategy APIs.
- Companion entry points and the `StringStrategy` property/data-class methods
  needed for Java use are retained without Kotlin runtime types. Kotlin compiler
  metadata and compiler-generated default-argument machinery are not retained;
  consumers should recompile against the Java API.
- The upstream `SyntaxErrorListener` was in the default package. Java cannot
  import it into a named package. Its implementation now lives in
  `com.sipios.springsearch.listeners`, with the original default-package class
  retained as a deprecated compatibility alias. Diagnostic formatting is fixed
  to the upstream format.

## Original test harness adaptation

There is one mechanical adaptation in the original test class: all **121**
instances of `SearchSpec::class.constructors.first().call(...)` now invoke the
test-only `searchSpec(...)` helper. A Java annotation has no Kotlin reflection
constructor. The helper constructs the Java annotation using named arguments,
preserving the original search parameter, case flag, and field blacklist.
The now-unused annotation import is removed.

After normalizing precisely those substitutions and that import, the entire
original test class matches the pinned upstream source byte for byte. All 113
method identities, queries, fixture values, assertions, and exception expectations
remain intact. The original fixtures and test resources are unchanged. No original
tests were deleted, disabled, or weakened.

## Verification and evidence

Both implementations were built with the unchanged Maven 3.6.0 wrapper, BellSoft
OpenJDK 17.0.20+10-LTS, and H2 2.2.220 on the same macOS ARM64 environment.
The pinned Kotlin revision is `c4b404dc6cf22b540d3e7f6211e557f5d83b6c61`.

| Result | Pinned Kotlin implementation | Java production implementation |
| --- | ---: | ---: |
| Original scenarios discovered/executed | 113 / 113 | 113 / 113 |
| Original scenarios passed | 113 | 113 |
| Original failures / errors / skips | 0 / 0 / 0 | 0 / 0 / 0 |
| Missing or unexpected original scenarios | 0 | 0 |
| Additional Java checks | 0 | 3 |
| Total tests passed | 113 | 116 |
| Full verification build exit code | 0 | 0 |

The original scenario names and outcomes match both the pinned run and the
[committed inventory](../baseline/scenarios.json). Declared dependency versions,
property versions, and resolved version coordinates also match. Scope changes
for the three Kotlin dependencies are recorded separately.

The Java caller check constructs configuration from `@SearchSpec`, builds a
specification, and executes it against H2 using an entirely Java entity and
Hibernate bootstrap, without a Spring context. It verifies the unconfigured
builder and a case-insensitive explicit pattern. Its standalone `main` also runs
successfully against the **packaged JAR** with Kotlin and Jackson Kotlin artifacts
excluded from the classpath. Bytecode inspection finds no production references
to Kotlin or Jackson Kotlin types. Two Java MVC checks cover resolver registration,
parameter selection, configured binding, absent/empty input, blacklist rejection,
and parser diagnostics. They supplement rather than replace the original corpus.

The Java build retains the upstream 80% instruction / 75% branch checks. The
configuration-package exclusion has been removed, so handwritten MVC production
code is now measured as well. Only generated ANTLR sources remain excluded.
Measured handwritten Java coverage is:

- Lines: **279 / 325 (85.85%)**.
- Instructions: **1,750 / 2,108 (83.02%)**.
- Branches: **218 / 283 (77.03%)**.

All handwritten production code is newly translated in this change, so line
coverage measures the translated executable lines rather than hiding them behind
unchanged production code. Remaining gaps include companion compatibility accessors,
the default-package alias, data-class accessors/copy methods, and some conversion,
reflection, and null-operation paths. Passing the corpus does not prove complete
behavioral equivalence. The other coverage gaps recorded in Stage 0 still apply;
add missing characterization tests against both implementations before relying
on their outcomes as compatibility evidence.

[results.json](results.json) preserves scenario outcomes, version coordinates,
coverage counters, production-source hashes, and evidence fingerprints. Raw local
logs and copied reports are under `build/stage1/`. CI retains native Maven logs,
Surefire and JaCoCo reports, effective POM/dependency metadata, and the standalone
Java caller output. It does not introduce a custom comparison script. GitHub CI
has not been executed locally.

## Reproduce and next stage

With JDK 17 selected, run:

```sh
./mvnw --batch-mode -Dgpg.skip=true -Ddependency-check.skip=true clean verify
mkdir -p build/stage1
./mvnw --batch-mode org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath -DincludeScope=test -DexcludeGroupIds=org.jetbrains.kotlin -DexcludeArtifactIds=jackson-module-kotlin -Dmdep.outputFile=build/stage1/java-caller-classpath.txt
java -cp "target/test-classes:target/spring-search-0.3.0.jar:$(cat build/stage1/java-caller-classpath.txt)" com.sipios.springsearch.JavaCallerSmokeTest
```

Artifact signing and the dependency vulnerability scan are skipped as in the
baseline test build; tests and coverage are enabled. No `install` or `deploy` is
required. Git commit signing is separate and remains enabled.

Next is Stage 2: freeze this Java production implementation, port the Kotlin
tests/fixtures to Java while retaining a scenario mapping, and run both suites
against that same implementation before removing Kotlin tooling. The Spring
Boot 4 upgrade and matching improvements remain later stages.
