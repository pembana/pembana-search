# Module split and immutable query model

Baseline: signed revision `a6448bc` (`1.0.0-SNAPSHOT`). This milestone introduces
three library artifacts under the existing `pembana-search` parent POM:

| Module | Responsibility | Tests per matrix job |
| --- | --- | ---: |
| `pembana-search-core` | Immutable records, expression parser, immutable parser policy and transport-independent diagnostics | 26 |
| `pembana-search-data-jpa` | Shared model translation, existing JPA entry points/strategies, original corpus and JPA characterization | 151 |
| `pembana-search-spring-boot-starter` | Optional servlet binding, conditional Boot configuration and resolver override | 8 |
| **Total** | **153 retained methods + 32 new acceptance methods** | **185** |

All six Java 17/21/25 runtime × H2/PostgreSQL combinations discovered and executed
185 methods with **zero failures, errors or skips**. Builds use Temurin 21.0.10
with `--release 17`; compiled production/test classes have major version **61**.
Each combination also passes the separate Java caller against packaged JPA/core
JARs. PostgreSQL uses Testcontainers 18.1; default H2 requires no Docker.
CI has been updated for the same reactor matrix, module reports and artifact
checks; these results are local execution, not a claim of a GitHub workflow run.

## Retained behavior and test identity

[scenarios.json](scenarios.json) maps every retained class/method to its current
module and lists the 32 additions. A source audit compares all 153 scenario
bodies against the baseline, removing only comments and whitespace outside
string/character literals. Inputs, assertion calls, expected values and exception
checks match. The original 113 methods remain in
`PembanaSearchCompatibilityTest`; its empty `run()` still makes no behavioral
assertion and is not presented as meaningful query coverage.

`QueryBehaviorTest` keeps its 26 JPA scenarios in the JPA module. Its one HTTP
method, `httpBindingExecutesSearchAndRendersQueryErrors`, moves unchanged into
`MvcQueryBehaviorTest`, with the same seed data, endpoint and error advice.
`JavaResolverTest` and the four existing auto-configuration methods move to the
starter. Fixture sources, defaults, persistence mappings, database resources and
the ANTLR grammar match the baseline byte for byte. The test-scoped JPA fixture
JAR shares these classes with starter tests without creating a reactor cycle or
adding fixtures to runtime dependencies. Surefire does not rediscover tests from
that dependency JAR.

Parsing now produces `Comparison`, `Membership`, `Junction` and `Negation`
records. Programmatic constructors produce equal models without expression
concatenation/reparsing. Tests cover all scalar operators, outer wildcard mapping,
null/empty states, inclusive and negated ranges, grouping/precedence, membership,
quote handling, immutable list copies, field restrictions, diagnostics and limits.
JPA tests compare programmatic/parsed results on the same database and verify
literal values, mandatory-scope composition and retained visitor entry points.
Default matching quirks, typed conversion and association semantics remain in
the existing strategies and specification implementation.

## Dependency boundaries and consumer control

Core's actual test classpath excludes Spring, persistence APIs, drivers and
Kotlin. The resolved dependency graph includes only ANTLR/JSpecify plus JUnit
and its test dependencies. JPA tests prove the starter is absent; the packaged
caller executes predicates without starting a Spring context. The JPA adapter
retains Spring Web for compatible HTTP error types and Jackson 2 for Date
conversion, without depending on Boot/MVC binding in its main graph.

A consumer can supply a `HandlerMethodArgumentResolver` bean named
`pembanaSearchSpecificationResolver`. The default bean backs off and registration
uses the replacement exactly once. A Boot context test verifies this path;
existing direct `new ResolverConf()` registration and conditional discovery tests
remain intact. See the [configuration reference](../reference/configuration.md).

## Coverage, packaging and limits of the evidence

Each module independently passes the unchanged **80% instruction / 75% branch**
coverage gates, with only generated grammar code excluded. On the final
PostgreSQL/Java 25 run:

| Module | Instructions | Branches |
| --- | ---: | ---: |
| Core | 99.2% | 92.6% |
| JPA | 86.5% | 79.6% |
| Starter | 100% | 100% |

Changed executable production lines have **221/221 covered** by JaCoCo source
line data compared with the baseline (100%, above the 80% requirement). This is
line coverage, not proof of exhaustive behavior. Existing mutable criteria,
companion accessors and strategy helper APIs still have coverage gaps; the
per-module reports retain these findings rather than excluding them. Parser
branches protected by the grammar and provider-specific invalid combinations
also remain subject to the characterized limits in the query reference.

All modules pass Checkstyle, strict Javadoc and production/test nullability
checking with no added suppressions. Ten binary/source/Javadoc/test-fixture JARs
retain byte-identical `LICENSE`, `LICENSE.txt` and `NOTICE` payloads from the
repository. The core source JAR also contains the original grammar. Inherited
source notices and verified original authors remain intact in moved/extracted
classes; new code has 2026 Apache 2 headers, type author tags and method Javadoc.

[results.json](results.json) records per-module/runtime counts, report hashes,
coverage and changed-line evidence. Native logs, Surefire XML, dependency trees,
Checkstyle, coverage and packaged-caller output remain under ignored
`build/modules/`. CI retains equivalent reports even when a build fails. Maven
artifact signing and the NVD scan are skipped for these compatibility commands;
Git commits remain signed. No artifact is deployed, tagged or published.

The release requirements are not all complete: improved matching/simple search,
field aliases/extension registries, a standalone consumer sample and publication
configuration remain separate work. Scalar model values intentionally stay as
text until an adapter resolves entity types; the core parser does not pretend to
know an application's schema. Expression limits apply to expression strings,
not programmatically built trees. These are documented boundaries of this
milestone, not silent changes to legacy matching.
