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
match the same root record, each in any eligible field or collection element, in any order. With any
star, the trimmed input becomes one anchored whole-value pattern: stars match
zero or more characters, including line breaks. Spaces inside that pattern are
literal. `sab* min*` must match one field value or collection element. Only `*` is special; quotes,
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
Nonblank input without configured fields is a configuration error. Eligible
fields are unique property paths with alphabetic-leading alphanumeric
segments separated by dots. Root String properties, to-one chains such as
`accession.taxon.genus.name`, embedded scalar properties, collection child String
fields, and String element collections are supported. Unknown attributes and
non-String targets fail when constructing the JPA predicate. Existing exact
allow/deny field restrictions apply before translation, with blacklist precedence.
Case-option keys must name eligible fields. Neutral input does not resolve entity
metadata. `STANDARD` null/empty/whitespace behavior remains unchanged.

For example, configure `List.of("title", "author.name", "variants.color", "labels")`
where `variants` is an entity collection and `labels` is a String element
collection. No `QueryExtensions` toggle is required for SIMPLE paths. Profiles
can expose aliases for these paths with the same permissions and case choices.

To-one traversal uses left joins. A missing relation, null scalar, or empty
collection supplies no match for that field, but another eligible field can still
match. Collection matches use correlated `EXISTS` queries; multiple matching
children do not duplicate root records or distort counts and pages. Different
words may match different children or different collections: `red large` can
match a red small variant and a blue large variant. A star pattern such as
`red*large` must match one complete scalar value or collection element.

Path navigation follows the existing association limits: at most one collection
per path, with supported to-one chains and embedded scalar navigation before or
after it. Maps, nested collections, collections inside embedded objects, and
association navigation after an embedded attribute are rejected with
`UNSUPPORTED_OPERATION`. Unknown fields report `UNKNOWN_FIELD`; non-String
properties or elements report `UNSUPPORTED_FIELD_TYPE`. Diagnostics include the
full canonical field path. Direct translation of collection fields requires a
Criteria query context; scalar paths also work without one.

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

The executable consumer example is
[`PlainTextJdbcConsumer`](../../pembana-search-data-jpa/src/test/java/com/pembana/search/samples/PlainTextJdbcConsumer.java).
`PlainTextJpaTest.jdbcJpaAndMemoryParity` compares its bound JDBC queries with
JPA results and in-memory matching on H2 and PostgreSQL, in both case modes.
The corpus includes literal SQL punctuation, neutral input, nulls, empty strings,
word grouping, anchored/repeated stars, newlines, and Latin accented text. These
checks do not establish equivalence for every Unicode character or collation.
