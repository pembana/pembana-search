# Pembana Search changelog

The [archived upstream changelog](docs/upstream/CHANGELOG.md) describes Spring Search.

## Unreleased

Pembana Search brings Spring Search’s JPA query language to Java and Spring Boot 4.
The parser, JPA integration, and optional Boot starter are available as separate modules.

### ⚠️ Upgrading

- Use the `com.pembana.search` Maven group and Java packages. Choose the core,
  JPA, or Boot starter artifact for your application. The parent POM aggregates
  the modules. Update imports from `anotation` to `annotation` and use
  `com.pembana.search.listeners.SyntaxErrorListener` if you used the upstream
  default-package alias. [Module and coordinate migration](docs/migration.md#migrating-the-single-snapshot-to-modules)
- Strict validation now rejects invalid and oversized expressions by default,
  with limits of 4,096 characters and 16 nesting levels. For legacy input handling,
  explicitly set `strictValidation=false` and both limits to zero.
  [Validation migration](docs/migration.md#strict-validation-and-input-limits-by-default)
- Mixed Boolean expressions now evaluate `AND` before `OR`. Add parentheses
  where your application relies on the previous grouping.
  [Boolean precedence migration](docs/migration.md#boolean-operator-precedence-correction)
- `caseSensitiveFlag=false` now applies consistently to string comparisons.
  Earlier case-insensitive queries can return different results.
  [Case sensitivity migration](docs/migration.md#consistent-case-sensitivity-for-membership-and-ordering)
- Build with JDK 21 and use Java 17 or later at runtime. The Boot starter targets
  Spring Boot 4.1.2 and uses the application’s Jackson 3 mapper for JSON binding.
  [Compatibility and setup](docs/getting-started.md)

### ⭐ New Features

- SIMPLE search supports nested string fields and collections of strings.
  [88c893c](https://github.com/pembana/pembana-search/commit/88c893c)
- Select a named profile with `@SearchSpec` to share aliases, operation permissions,
  and case defaults across GET and JSON requests.
  [e870eeb](https://github.com/pembana/pembana-search/commit/e870eeb)
- Bind JSON search requests explicitly with `@SearchRequestBody`.
  [HTTP field profiles](docs/reference/profiles.md#use-a-profile-in-an-http-endpoint)
- Parse expressions or construct immutable query trees directly. Apply field
  allowlists and per-query field policies to either form.
  [19353b9](https://github.com/pembana/pembana-search/commit/19353b9)
- Configure case sensitivity globally or per property.
  [ed25d2e](https://github.com/pembana/pembana-search/commit/ed25d2e)
- Match Java `Long` fields with exact conversion and overflow validation.
  [2c8d3bc](https://github.com/pembana/pembana-search/commit/2c8d3bc)
- Customize value conversion and predicate strategies, including opt-in support
  for optional to-one associations.
  [Extension and association guide](docs/migration.md#explicit-converters-and-predicate-extensions)

### 🐞 Bug Fixes

- Resolve mapped collections for emptiness queries and convert nested enum values
  against the resolved enum type. [2cdd0b3](https://github.com/pembana/pembana-search/commit/2cdd0b3)
- Honor the case policy in equality, membership, ordering, and ranges, including
  basic collection elements. [1dcf617](https://github.com/pembana/pembana-search/commit/1dcf617)
- Bound regular-expression work during query trimming and path validation.
  [d56e815](https://github.com/pembana/pembana-search/commit/d56e815)

### 📔 Documentation

- See the migration guide for package changes, matching behavior, and compatibility
  options. [Migration guide](docs/migration.md)
- Review the Java 17, 21, and 25 compatibility checks against H2 and PostgreSQL.
  The checks cover the original 113 scenarios, JDBC and JPA matching parity, and
  source and binary API compatibility. [Search acceptance](docs/search-acceptance.md)
- See the release guide for signed Maven Central publication and the development
  guide for opt-in dependency scans. [Release guide](docs/releasing.md) ·
  [Dependency scanning](docs/development.md#dependency-check-and-nvd)

### 🔨 Dependency Upgrades

- Spring Boot 4.1.2, ANTLR 4.13.2, H2 2.5.252, Jackson 2.22.4 and 3.2.4,
  and JUnit 6.1.3. [991b235](https://github.com/pembana/pembana-search/commit/991b235)

### ❤️ Contributors

- [Gunnar Hillert](https://github.com/ghillert)
