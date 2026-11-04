# Query behavior and validation

This guide describes the current advanced-expression API. Unconfigured
`@SearchSpec` and `SpecificationsBuilder` retain the inherited matching policy.
Strict validation changes rejection behavior only when explicitly enabled.
Neither mode introduces ordinary-text word search, fuzzy matching, aliases, or
automatic wildcard expansion for equality.

## Expressions and matching

| Expression | Current behavior |
| --- | --- |
| `userFirstName:Ada` | Equality; no implicit substring search |
| `userFirstName!Ada` | Inequality, subject to SQL null semantics |
| `userFirstName:Ada*` | Starts with `Ada` |
| `userFirstName:*da` | Ends with `da` |
| `userFirstName:*da*` | Contains `da` |
| `userFirstName!*da*` | Negated contains |
| `userChildrenNumber>2`, `>:2`, `<2`, `<:2` | Greater, greater/equal, less, less/equal |
| `userFirstName IN [Ada,Beta]` | Typed membership; `NOT IN` negates it |
| `userChildrenNumber BETWEEN 2 AND 4` | Inclusive lower and upper comparisons |
| `userChildrenNumber NOT BETWEEN 2 AND 4` | Negation of the inclusive range |
| `userLastName IS NULL`, `IS NOT NULL` | SQL null checks |
| `books IS EMPTY`, `IS NOT EMPTY` | Collection emptiness/existence checks |
| `author.name:Writer` | Entity-path navigation; implicit joins can affect OR results |

Operators and keywords are case-sensitive. `AND` and `OR` share precedence and
associate left to right: `A OR B AND C` means `(A OR B) AND C`. Parentheses
override that order. Use explicit grouping rather than assuming SQL precedence.
Membership and ranges use the same field conversions as scalar comparisons.
An empty `IN []` matches no rows; empty `NOT IN []` matches all rows in the tested
Hibernate/H2/PostgreSQL baseline, including rows with null field values.

The lexer distinguishes identifiers, numbers, Boolean tokens, quoted strings,
and encoded strings. Single/double-quoted values can contain spaces and query
punctuation. The visitor removes surrounding quotes and unescapes escaped single
and double quotes; it does not implement general JSON/JavaScript escape decoding.
The grammar's identifier alphabet limits entity keys to letters, digits, and dots.
Grammar acceptance alone does not establish that a field exists or a value is valid.

Only outer `*` markers select wildcard operations. Internal `*` remains literal;
this API does not implement arbitrary glob patterns. SQL `%` and `_` inside a
pattern retain SQL LIKE wildcard semantics. For example, `userFirstName:'*%*'`
matches every non-null first name, and `userFirstName:'A_a*'` matches `Ada`.
Strict validation deliberately leaves those matching semantics unchanged. Literal
SQL-metacharacter escaping needs a separate explicit matching-policy change.

`caseSensitiveFlag=true` is the default. With `false`, wildcard predicates fold
the field using SQL `lower` and the pattern using Java `Locale.ROOT`. Greater-than
comparisons also use the folded field, but equality/membership use the original
field and value, and less-than comparisons retain the original field with a
folded value. Thus `*ADA*` can match `Ada` while equality to `ADA` does not.
This inherited asymmetry is characterized rather than corrected. SQL collation,
non-ASCII case handling, accent sensitivity, and backslash behavior can depend on
the database; the suite does not promise identical behavior for every collation.

Supported conversion strategies cover String, Integer/int, Float/float,
Double/double, Boolean/boolean, Date, Duration, LocalDate, LocalTime,
LocalDateTime, Instant, UUID, and top-level enums. Java time values use their
existing parsers; Date retains Jackson 2 `StdDateFormat`. Enum conversion remains
case-sensitive and retains its existing nested-enum limitation. Types without a
strategy fall back to string handling in compatibility mode. Null checks are
distinct from equality to a string such as `'NULL'`.

## Optional validation and field policy

```java
@GetMapping("/users")
public List<User> users(
        @SearchSpec(strictValidation = true,
                    allowedFields = {"userFirstName", "active", "updatedDateAt"},
                    maxQueryLength = 4096,
                    maxNestingDepth = 16)
        Specification<User> search) {
    // Compose consumer-owned restrictions outside the user expression.
    Specification<User> effective = search == null ? tenantScope : tenantScope.and(search);
    return repository.findAll(effective);
}
```

These limits are an application example, not library defaults. The settings are
independent; allowlists and limits can be used without strict type validation.

| Setting | Default | Effect when configured |
| --- | --- | --- |
| `strictValidation` | `false` | Reject unknown paths, unsupported field types/operators, invalid Booleans, empty scalar strings, and any lexer diagnostics |
| `allowedFields` | `{}` | Non-empty lists permit exact entity paths only, across every expression branch |
| `blackListedFields` | `{}` | Exact forbidden paths, taking priority over the allowlist |
| `maxQueryLength` | `0` (unlimited) | Inclusive maximum Java UTF-16 string length before lexing |
| `maxNestingDepth` | `0` (unlimited) | Inclusive maximum parenthesis depth, checked on tokens before recursive parsing |

Negative limits are consumer configuration errors (`IllegalArgumentException`),
not bad user queries. Parentheses inside quoted strings do not count toward
depth. Depth limits bound explicit grouping, not expression count or complete AST
depth; configure length too when constraining input size. Token/depth checks do
not validate SQL complexity, execution time, or authorization.

Field lists are exact paths: allowing or denying `author` does not imply a rule
for `author.name`. There are no aliases, automatic subtrees, or entity-field
discovery endpoints. These lists apply to parsed expressions; constructing
arbitrary Specifications directly remains the consumer's responsibility. Lists
supplied through annotation attributes have Java's
normal annotation semantics. Applications manually implementing `SearchSpec`
must implement the four new members and include them in annotation equality and
hashing; normal annotation usages keep their existing defaults.

Strict validation permits equality/inequality/membership for supported scalar
types; ordering/ranges for strings, the supported numeric types, and temporal
types; patterns for strings; NULL checks on scalar paths; and EMPTY checks on
collections. Boolean values must be `true` or `false`, compared ignoring case,
including every item of a membership list. It rejects ordering on Boolean,
UUID, and enum fields, numeric wildcards, unsupported scalar types such as an
association object, and unsupported collection operations before building SQL.
It retains the existing conversion strategies, matching, joins, and null handling.
Conversion and field checks occur when JPA constructs a predicate; syntax,
limits, and field-list checks occur while building the specification.

In compatibility mode, invalid Boolean text still becomes `false`; Boolean
ordering can contribute no predicate; unknown paths still surface provider
errors; and an empty quoted scalar still raises the inherited null-pointer
failure. Strict mode turns these cases into explicit query errors instead of
silently changing the default policy. An empty membership list remains valid.

## Errors and HTTP integration

`InvalidSearchQueryException` extends `ResponseStatusException` with HTTP 400,
`getErrorCode()`, and an optional full path from `getField()`. Its ProblemDetail
contains `code`, optional `field`, and syntax `line`/`column` where available
(ANTLR's one-based line and zero-based column). Existing syntax, blacklist, and
conversion reason text is retained in compatibility mode; those errors now use
this more specific exception subclass. Existing unsupported-operation errors
outside strict mode retain their original exception behavior.

| Code | Meaning |
| --- | --- |
| `SYNTAX_ERROR` | Invalid expression, with parser/lexer location text |
| `QUERY_LIMIT_EXCEEDED` | Configured input length or grouping depth exceeded |
| `FIELD_NOT_ALLOWED` | Blacklist or allowlist rejection |
| `UNKNOWN_FIELD` | Strict JPA path resolution failed |
| `INVALID_VALUE` | Conversion failure, invalid strict Boolean, or empty strict scalar |
| `UNSUPPORTED_FIELD_TYPE` | No supported strict scalar conversion strategy |
| `UNSUPPORTED_OPERATION` | Strict field/operator incompatibility |

The library installs no global controller advice or mandatory HTTP response
format. Applications can render `exception.getBody()` themselves or use a
`ResponseEntityExceptionHandler` through their own `@ControllerAdvice` (or Boot's
ProblemDetail support). The HTTP test demonstrates real resolver binding,
database execution, status 400, and JSON `code`/`field` through consumer advice.
Structured metadata does not echo the rejected value; existing human-readable
ANTLR reasons can contain input fragments.

The MVC resolver reads the configured parameter (`search` by default) and returns
null when it is absent or exactly empty. Whitespace-only input is still a syntax
error. A builder without `withSearch` yields the existing null-predicate
specification. Consumers should handle absent search explicitly when calling
repository APIs that require a non-null Specification; validation does not add
mandatory tenant or authorization restrictions.

## Associations, counts, and remaining gaps

Tests compose a mandatory tenant predicate as `tenant.and(userSearch)` so user
OR expressions cannot escape that restriction. Collection existence combined
with OR is tested with multiple child rows, accurate totals, and a sorted page.
Scalar queries have explicit sorting, two pages, and consistent counts on both
databases. These cases do not establish arbitrary join/pagination correctness.

`author.name:Writer OR title:Orphan` on Book retains the implicit inner join:
a Book with null author is excluded even if its title matches the other branch.
That regression test records the limitation; no left-join/EXISTS rewrite is
introduced. To-many path navigation, all association topologies, aliases,
field/operator registries, an immutable Spring-independent query model, extension
points, ordinary-text word matching, internal glob syntax, literal SQL escaping,
and comprehensive collation behavior remain separate changes. The inherited
empty `run()` test still provides no behavioral assertion.
