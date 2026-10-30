# Plain-text search and per-query modes

Existing constructors, `withSearch`, `withQuery`, and HTTP binding retain advanced
expression behavior. Plain text is selected explicitly through the Java API;
there is no syntax detection or fallback after parsing fails.

```java
var configuration = new SearchConfiguration(
        SearchMode.SIMPLE, List.of("title", "summary"), 1024);
var builder = SpecificationsBuilder.<Book>configured(
        searchSpec, SearchOptions.defaults(), configuration);

// Inherit this consumer's plain-text default.
Specification<Book> text = builder.withInput("minor sabal").build();
// Override for one query; this replaces the builder's previous query.
Specification<Book> standard = builder.withInput(
        "title:Sabal", SearchMode.STANDARD).build();
// Select plain text even when the consumer default is STANDARD.
Specification<Book> explicit = builder.withInput(
        "sab*al", SearchMode.SIMPLE).build();
```

The types are `com.pembana.search.SpecificationsBuilder` and
`com.pembana.search.core.SearchConfiguration`, `SearchMode`, and `SearchOptions`.
Reuse immutable configuration across requests; create a builder for each request.
`STANDARD` selects the original field/operator/Boolean expression syntax, which
is the library default. `SIMPLE` selects plain-text word and star-pattern matching.
`INHERIT` uses the consumer default, which must be `STANDARD` or `SIMPLE`. A consumer
configured with `SIMPLE` therefore gets plain text for inherited queries; explicitly
selecting `STANDARD` always selects the original expression syntax.
`SearchConfiguration.defaults()` selects `STANDARD` mode, no text fields, and a
1024 generated-predicate budget. Selecting `STANDARD` mode again deactivates text
interpretation for that query. Neither configuration nor classpath presence
changes existing MVC binding. Applications choose how to expose a mode in their
own request contracts. `withSearch` always uses the standard expression syntax regardless of
configuration; `withQuery` retains its existing programmatic interpretation.

Without a star, input is trimmed and split on Unicode whitespace. All words must
match the same root record, each in any eligible field, in any order. With any
star, the trimmed input becomes one anchored whole-value pattern: stars match
zero or more characters, including line breaks. Spaces inside that pattern are
literal. `sab* min*` must match one field value. Only `*` is special; quotes,
Boolean words, backslashes, SQL `%`/`_`, and regex punctuation are literal. There
is no literal-star escape syntax in this preset. Repeated stars are equivalent.

Plain text defaults to insensitive matching. Existing explicit per-field case
choices override the global choice; an unresolved `INHERIT` uses this preset's
insensitive default. The legacy annotation case flag remains the `STANDARD` mode
fallback. Java normalization uses `Locale.ROOT`; database lower/collation behavior
can differ for Unicode and accents. No universal Unicode case equivalence or
accent removal is promised.

Null, empty, and whitespace-only text produce a neutral specification. `*`
matches non-null values, including empty strings; all-null records do not match.
Nonblank input without configured fields is a configuration error. This increment
supports root String properties only, with unique alphabetic-leading alphanumeric
names. Association/dotted paths are rejected at configuration time; unknown and
non-String attributes fail when constructing the JPA predicate. Existing exact
allow/deny field restrictions apply before translation, with blacklist precedence.
Case-option keys must name eligible fields. Neutral input does not resolve entity
metadata. `STANDARD` null/empty/whitespace behavior remains unchanged.

The existing `maxQueryLength` limits raw input before composition. The positive
`maxTextPredicates` budget bounds words times fields (or one pattern times fields)
and rejects excess work with `QUERY_LIMIT_EXCEEDED`. `maxNestingDepth` applies to
advanced expressions; plain text has no recursive grouping. In-memory glob
matching uses dynamic programming, not regex backtracking, with time proportional
to pattern length times candidate length and memory proportional to candidate
length. Consumers should bound stored/input sizes for their workloads.

`PlainTextQuery.compose(input, configuration, parserOptions)` exposes composition
without Spring or JPA. It preserves AND across patterns and OR across fields.
`TextPattern.matches(value, sensitive)` evaluates an individual nullable field.
`PlainTextSpecifications.translate(composed, options)` creates a JPA specification;
when used directly, consumers must apply their field policy during composition.
JPA predicates run before pagination and counting. Mandatory scopes remain caller
owned: compose `tenantScope.and(userSearch)` around the complete user expression.
No library sorting or fetch joins are introduced.

For consumer-owned JDBC statements, the same immutable pattern returns parameter
data using the fixed SQL escape character `!`:

```java
TextPattern pattern = new TextPattern("100%", false);
try (PreparedStatement statement = connection.prepareStatement(
        "select id from catalog where lower(title) like ? escape '!'")) {
    statement.setString(1, pattern.sqlLike(false)); // %100!%%, bound as data
    try (ResultSet results = statement.executeQuery()) {
        // Read matching identifiers; compose other predicates in application code.
    }
}
```

This is a pattern adapter, not a general SQL compiler. Existing advanced patterns
retain their historical outer-star and SQL-metacharacter interpretation. New
pattern semantics never rewrite an existing `Comparison(EQUALS, value)`.
