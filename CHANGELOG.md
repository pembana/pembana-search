# Pembana Search changelog

This is Pembana Search's independent version history. No version is released yet.
The [archived upstream changelog](docs/upstream/CHANGELOG.md) describes Spring Search.

## 1.0.0-SNAPSHOT — unreleased

### Added

- Opt-in `security` Maven profile with one reactor-wide aggregate Dependency-Check
  report, plus nightly/manual scans with a versioned, persistent database cache.

- Opt-in multi-module local Maven staging and manual JReleaser publication for
  Central snapshots/releases, with reusable compatibility CI, dry-run mode and
  documented signing credentials.

- Immutable global/per-property case-sensitivity choices with property precedence.
- GET/form option binding and explicit JSON search binding through `@SearchRequestBody`.
- Strict option/envelope validation, application Jackson 3 mapper integration and
  opt-in consistent String membership/ordering without changing inherited defaults.

- Independent core, JPA and optional Boot starter modules.
- Immutable query trees shared by parsing and programmatic construction.
- Field-policy enforcement for programmatic trees and a replaceable named MVC resolver.
- Core equivalence/dependency-boundary and direct JPA model tests.

- Java implementation and tests preserving all 113 original scenarios.
- H2 and Testcontainers PostgreSQL verification on Java 17, 21, and 25.
- Opt-in strict validation, field allowlists, query limits, and structured errors.
- Matching/association characterization tests and current Java documentation.
- Spring JavaFormat Checkstyle 0.0.48 enforcement for handwritten production/test Java.

### Fixed

- Correct an inherited Boolean precedence bug: `AND` now binds more tightly than
  `OR`. Mixed expressions without parentheses may return different results.
  Write `(A OR B) AND C` to preserve the earlier meaning of `A OR B AND C`.
  See the [migration guide](docs/migration.md#boolean-operator-precedence-correction).

- Resolve collection attributes through JPA metadata so inherited and getter-mapped
  collections support emptiness queries, including dotted association paths.
- Convert nested enum values through the resolved enum class, including private
  nested types, while retaining exact names and structured invalid-value errors.

- String equality and inequality now honor `caseSensitiveFlag=false`, folding
  both field and input. The true default and membership behavior are unchanged.
- Documented the intentional false-flag behavior change, null/collation limits,
  recommended case-insensitive form defaults and proposed per-field controls.

### Changed

- Enable strict validation by default in `@SearchSpec` and core parser defaults,
  with limits of 4,096 characters and 16 nesting levels. Invalid and oversized
  expressions now fail explicitly. Configure larger limits when needed; select
  `strictValidation=false` and zero limits explicitly for legacy input handling.
  Searchable-field restrictions remain application-defined. See
  [migration advice](docs/migration.md#strict-validation-and-input-limits-by-default).

- Spring Boot baseline is 4.1.1; Kotlin tooling has been removed.
- Maven and style tools build on JDK 21 while compiler release targeting and
  consumer compatibility remain Java 17; CI tests runtimes 17, 21, and 25.
- `com.pembana:pembana-search:1.0.0-SNAPSHOT` is the parent/aggregator; applications
  select the core, JPA or Boot starter artifact at the same version.
- Java packages use `com.pembana.search`, with `annotation` replacing `anotation`.
- Test application/corpus names use Pembana Search; historical identities are mapped.
- Upstream publishing defaults and obsolete release automation are removed;
  deployment is disabled pending Pembana's release configuration.

### Compatibility

- Import and coordinate changes require consumer migration; there are no old-package shims.
- Boolean precedence now follows `AND` before `OR`; explicit parentheses retain
  control of grouping. Other matching corrections are described above. Stricter
  input rejection is now the default; compatibility behavior requires explicit configuration.
- The deprecated upstream default-package `SyntaxErrorListener` alias is removed;
  use `com.pembana.search.listeners.SyntaxErrorListener` when needed.
- The parent POM aggregates separate core, JPA, and optional Boot starter artifacts.
