# Architecture

Pembana Search is currently one Java 17 artifact, `com.pembana:pembana-search`.
It integrates an ANTLR grammar, Spring Data JPA predicates and optional MVC binding:

```text
HTTP query parameter (optional MVC resolver)
        |
        v
SpecificationsBuilder / CriteriaParser + SearchSpec policy
        |
        v
ANTLR Query lexer/parser -> QueryVisitorImpl
        |
        v
composed JPA Specification objects
        |
        v
SpecificationImpl: entity path -> optional validation -> typed conversion
        |
        v
Criteria API predicate -> consumer repository / database
```

ANTLR 4.13.1 generates Java during Maven's generate-sources phase. The grammar
is inherited and unchanged. Field-access and input-limit checks occur during
parsing; JPA path/type checks need an entity root and occur later. Scalar/list
conversion and matching strategies retain legacy behavior by default.
Exceptions use Spring's ResponseStatusException/ProblemDetail infrastructure.

The current parser emits Specifications directly. There is no independent
immutable AST, ordinary-text word-search engine, or programmatic query DSL.
Consumers compose mandatory predicates, transactions and sorting themselves.
The MVC resolver reads one configured parameter and contributes no security scope.

## Dependencies and optional integration

Applications supply framework starters and database drivers. The library's
framework dependencies are provided, while ANTLR runtime and Jackson 2 date
conversion are ordinary dependencies. Testcontainers PostgreSQL exists only in
the test dependency graph. H2 is the default test engine and does not start Docker.

Boot 4 discovers conditional MVC configuration through its imports resource;
registration does not depend on the consumer scanning the library package.
The packaged-JAR smoke check executes JPA predicates without a Spring context.

## Planned boundaries

The requirements propose `pembana-search-core`, `pembana-search-data-jpa`, and
`pembana-search-spring-boot-starter`. These are future artifacts, not current
installation choices. A clean core first needs the independent query model and
diagnostics; splitting today's directly coupled parser into empty modules would
misrepresent those boundaries. Keep the single artifact until that work has its
own acceptance tests and compatibility review.

This housekeeping milestone changes identity, metadata and documentation, not
query semantics or dependency/framework versions. See the
[migration guide](migration.md) and [identity evidence](identity/README.md).
