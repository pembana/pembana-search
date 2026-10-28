# Configuration and public entry points

## `@SearchSpec`

Import `com.pembana.search.annotation.SearchSpec`. It targets method parameters
and is retained at runtime. MVC applies it only to `Specification` parameters.

| Member | Default | Contract |
| --- | --- | --- |
| `searchParam` | `"search"` | HTTP query parameter name |
| `caseSensitiveFlag` | `true` | Legacy text matching policy; false does not fold every operator |
| `blackListedFields` | `{}` | Exactly denied entity paths; wins over allowlists |
| `strictValidation` | `false` | Opt-in typed/path/operator validation and actionable rejections |
| `allowedFields` | `{}` | Nonempty exact allowlist for parsed expressions |
| `maxQueryLength` | `0` | Inclusive UTF-16 input length limit; zero is unlimited |
| `maxNestingDepth` | `0` | Inclusive grouping-depth limit; zero is unlimited |

Negative limits are configuration errors. Missing or exactly empty HTTP search
returns null; whitespace is not treated as absent. Policies are explicit Java
annotation settings; the library defines no `pembana.search.*` configuration
properties. Database test profiles are test resources, not production settings.
See the [query reference](query-language.md) for complete matching/error semantics.

## Entry points

| Type | Responsibility |
| --- | --- |
| `SpecificationsBuilder<T>` | Parse/replace an expression with `withSearch`, return a JPA specification with `build` |
| `CriteriaParser<T>` | Direct expression-to-specification parsing with an explicit policy |
| `InvalidSearchQueryException` | HTTP-400-compatible diagnostic, code/field accessors and ProblemDetail body |
| `QueryErrorCode` | Stable categories rather than parsing reason strings |
| `SearchCriteria`, `SpecificationImpl<T>` | Retained mutable/low-level adapter types; not the planned immutable DSL |
| `SearchSpecificationResolver` | MVC parameter binding |
| `ResolverConf` | Conditional Boot auto-configuration registering the resolver |

The parser visitor, generated grammar, strategies and legacy companion-style
helpers are implementation details retained through the migration. Their public
visibility is not a commitment to the future query model. No packages or module
names for unimplemented features are advertised as released API.

## Boot auto-configuration and consumer control

`com.pembana.search.configuration.ResolverConf` is listed in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
It activates for servlet applications when MVC is present. Direct JPA and
non-web contexts do not require the resolver configuration to activate.

To replace resolver registration, exclude this auto-configuration using Boot's
`spring.autoconfigure.exclude` setting or `@EnableAutoConfiguration(exclude =
ResolverConf.class)`, then register your resolver through an application-owned
`WebMvcConfigurer`. The library does not choose your entities, data source,
security policy, transaction boundaries, or controller routes.

The artifact uses provided Spring JPA/Web MVC/Data REST starter dependencies.
Jackson 2 databind is an explicit runtime dependency solely because the inherited
Date conversion uses `StdDateFormat`; Boot 4's Web MVC stack uses Jackson 3.
This does not enable a Jackson 2 HTTP mapper. H2, PostgreSQL JDBC and
Testcontainers are test-scoped. No Kotlin dependencies are resolved by this build.

## Error bodies

The library does not install global HTTP advice. Use application-owned
`ResponseEntityExceptionHandler`/`@ControllerAdvice` or enable Boot's
`spring.mvc.problemdetails.enabled` support to render ProblemDetail responses.
Codes, optional field paths, and syntax locations are available through the
exception's `getBody()`; human-readable reasons remain compatible.
Do not claim every provider or infrastructure error is an invalid user query.
Unknown paths and certain inherited invalid combinations retain provider errors
unless strict validation is enabled.

## API documentation

Package with Maven to generate `target/reports/apidocs/index.html` and the
`pembana-search-1.0.0-SNAPSHOT-javadoc.jar`. Javadoc generation fails on errors;
generated ANTLR API is excluded from the public reference. Binary, source and
Javadoc JARs contain `META-INF/LICENSE`, `META-INF/LICENSE.txt` and `META-INF/NOTICE`.
