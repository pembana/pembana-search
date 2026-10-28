# Pembana Search changelog

This is Pembana Search's independent version history. No version is released yet.
The [archived upstream changelog](docs/upstream/CHANGELOG.md) describes Spring Search.

## 1.0.0-SNAPSHOT — unreleased

### Added

- Java implementation and tests preserving all 113 original scenarios.
- H2 and Testcontainers PostgreSQL verification on Java 17, 21, and 25.
- Opt-in strict validation, field allowlists, query limits, and structured errors.
- Matching/association characterization tests and current Java documentation.

### Changed

- Spring Boot baseline is 4.1.1; Kotlin tooling has been removed.
- Coordinates are `com.pembana:pembana-search:1.0.0-SNAPSHOT`.
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
