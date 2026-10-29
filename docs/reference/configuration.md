# Configuration and public entry points

## `@SearchSpec`

Import `com.pembana.search.annotation.SearchSpec`. It targets method parameters
and is retained at runtime. MVC applies it only to `Specification` parameters.

| Member | Default | Contract |
| --- | --- | --- |
| `searchParam` | `"search"` | HTTP query parameter name |
| `caseSensitiveFlag` | `true` | False folds string equality, inequality and patterns; membership/ordering have documented limits |
| `blackListedFields` | `{}` | Exactly denied entity paths; wins over allowlists |
| `strictValidation` | `false` | Opt-in typed/path/operator validation and actionable rejections |
| `allowedFields` | `{}` | Nonempty exact allowlist for parsed and programmatic queries |
| `maxQueryLength` | `0` | Inclusive UTF-16 input length limit; zero is unlimited |
| `maxNestingDepth` | `0` | Inclusive grouping-depth limit; zero is unlimited |

Negative limits are configuration errors. Missing or exactly empty HTTP search
returns null; whitespace is not treated as absent. Policies are explicit Java
annotation settings; the library defines no `pembana.search.*` configuration
properties. Database test profiles are test resources, not production settings.
See the [query reference](query-language.md) for complete matching/error semantics.

For examples, migration implications and the current limits of field/global
controls, see [case sensitivity](case-sensitivity.md). The annotation is a static
policy; an HTTP case-toggle parameter has no automatic effect.

## Entry points

| Type | Responsibility |
| --- | --- |
| `SpecificationsBuilder<T>` | Replace a query with `withSearch` or `withQuery`; return a specification with `build` |
| `ExpressionParser`, `ParserOptions` | Framework-independent parsing and immutable input policy in core |
| `Query`, `Comparison`, `Membership`, `Junction`, `Negation` | Immutable programmatic/parsed query trees in core |
| `JpaQueryTranslator<T>` | Shared model-to-JPA translation with field restrictions |
| `CriteriaParser<T>` | Direct expression-to-specification parsing with an explicit policy |
| `InvalidSearchQueryException` | HTTP-400-compatible diagnostic, code/field accessors and ProblemDetail body |
| `QueryErrorCode` | Stable categories rather than parsing reason strings |
| `SearchCriteria`, `SpecificationImpl<T>` | Retained mutable/low-level adapter types; distinct from the immutable core model |
| `SearchSpecificationResolver` | MVC parameter binding |
| `ResolverConf` | Conditional Boot auto-configuration registering the resolver |

The parser visitor, generated grammar, strategies and legacy companion-style
helpers are implementation details retained through the migration. Their public
visibility does not make those compatibility details preferred extension APIs. The
immutable query model is in `com.pembana.search.core`; see the architecture guide.

## Boot auto-configuration and consumer control

`com.pembana.search.configuration.ResolverConf` is listed in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
It activates for servlet applications when MVC is present. Direct JPA and
non-web contexts do not require the resolver configuration to activate.

To replace request binding, declare a bean named
`pembanaSearchSpecificationResolver` whose type implements
`HandlerMethodArgumentResolver`. The default `SearchSpecificationResolver` bean
backs off, and the consumer resolver is registered once. This override is tested
through a real Boot context. Alternatively, exclude `ResolverConf` through
`spring.autoconfigure.exclude`/`@EnableAutoConfiguration` and register your own
`WebMvcConfigurer`. Direct construction with `new ResolverConf()` retains default
registration for compatibility and does not perform bean lookup.

Only the starter brings Boot JPA/MVC starters. Core depends on ANTLR/JSpecify;
the JPA module depends on Spring Data JPA, Jakarta Persistence, Spring Web (for
legacy error types) and Jackson 2 (for Date conversion). Drivers, H2,
Testcontainers and the shared test fixture JAR are test-scoped. No Kotlin
runtime/toolchain is required. Boot MVC uses Jackson 3; retaining Date conversion
with Jackson 2 does not install a Jackson 2 HTTP mapper.

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

Package with Maven to generate `<module>/target/reports/apidocs/index.html` and the
the per-module `*-1.0.0-SNAPSHOT-javadoc.jar` artifacts. Javadoc generation fails on errors;
generated ANTLR API is excluded from the public reference. Binary, source and
Javadoc JARs contain `META-INF/LICENSE`, `META-INF/LICENSE.txt` and `META-INF/NOTICE`.
