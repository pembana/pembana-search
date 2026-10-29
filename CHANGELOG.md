# Pembana Search changelog

This is Pembana Search's independent version history. No version is released yet.
The [archived upstream changelog](docs/upstream/CHANGELOG.md) describes Spring Search.

## 1.0.0-SNAPSHOT — unreleased

### Added

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

- String equality and inequality now honor `caseSensitiveFlag=false`, folding
  both field and input. The true default and membership behavior are unchanged.
- Documented the intentional false-flag behavior change, null/collation limits,
  recommended case-insensitive form defaults and proposed per-field controls.

### Changed

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
- Query matching defaults remain inherited. Stricter input rejection is opt-in.
- The deprecated upstream default-package `SyntaxErrorListener` alias is removed;
  use `com.pembana.search.listeners.SyntaxErrorListener` when needed.
- This is a single artifact. The planned core/JPA/starter split is not implemented yet.
