# Case sensitivity

## Defaults and precedence

The existing `@SearchSpec.caseSensitiveFlag` default remains **true**. Existing
GET/form endpoints and Java calls that omit request options retain their current
behavior. Configure a normal text-search form to submit `caseSensitivity=insensitive`
when that is the desired default; exact identifier fields can override it.

Effective policy resolves in this order:

1. An explicit property choice in `fieldCaseSensitivity`.
2. The request-wide `caseSensitivity` choice.
3. The configured `@SearchSpec(caseSensitiveFlag=...)` fallback.

| Wire value | Java value | Meaning |
| --- | --- | --- |
| `sensitive` | `CaseSensitivity.SENSITIVE` | Retain field/value case; subject to the database's collation |
| `insensitive` | `CaseSensitivity.INSENSITIVE` | Apply SQL lower to String fields and ROOT normalization to values |
| `inherit` or omitted | `CaseSensitivity.INHERIT` | Use the next level of policy |

A property set to `inherit` uses an explicit global choice. If both levels inherit,
existing annotation behavior applies, including its legacy membership/ordering
rules. Options never select matching behavior by changing a query's capitalization.
Every occurrence of a property uses the same choice across AND/OR groups, ranges
and negation. Other properties retain the resolved global/fallback policy.

## GET and form requests

Readable GET syntax (URL-encode the parameters when sending):

```text
?search=name:ADA AND code:AbC
&caseSensitivity=insensitive
&fieldCaseSensitivity[code]=sensitive
```

`name` ignores case; `code` retains case. The existing `@SearchSpec Specification<User>`
argument binds these query/form parameters without an additional annotation.
`@SearchSpec(searchParam="filter")` changes the GET expression parameter to
`filter`; option parameter names remain fixed. Supply one complete dotted path
for associations, for example `fieldCaseSensitivity[author.name]=insensitive`.

A real request can be sent with:

```sh
curl --get http://localhost:8080/users \
  --data-urlencode 'search=name:ADA AND code:AbC' \
  --data-urlencode 'caseSensitivity=insensitive' \
  --data-urlencode 'fieldCaseSensitivity[code]=sensitive'
```

The old `?caseSensitiveFlag=false` spelling is not a new request override. That
name remains an annotation member; use the enum-valued `caseSensitivity` parameter.

## JSON request bodies

JSON binding is explicit so an existing POST controller can keep its own
`@RequestBody` payload and query/form search binding. Add
`com.pembana.search.configuration.SearchRequestBody` alongside `@SearchSpec`:

```java
/**
 * Executes an application-owned search policy and scope.
 * @param search resolved optional user query
 * @return authorized matching users
 */
@PostMapping(path = "/users/search", consumes = MediaType.APPLICATION_JSON_VALUE)
List<User> search(@SearchRequestBody
        @SearchSpec(strictValidation = true, allowedFields = {"name", "code"})
        @Nullable Specification<User> search) {
    return repository.findAll(search == null ? tenantScope : tenantScope.and(search));
}
```

Imports are `com.pembana.search.annotation.SearchSpec`,
`com.pembana.search.configuration.SearchRequestBody`,
`org.jspecify.annotations.Nullable` and Spring Data's `Specification`.
The application supplies its entity, repository and mandatory `tenantScope`.
Reserve the body for one JSON search argument; do not combine it with another
`@RequestBody` argument. Without `@SearchRequestBody`, `@SearchSpec` continues to
read query/form parameters, even on a POST with a JSON business payload.

```json
{
  "search": "name:ADA AND code:AbC",
  "caseSensitivity": "insensitive",
  "fieldCaseSensitivity": {
    "code": "sensitive"
  }
}
```

The JSON expression member is always `search`, independently of the annotation's
GET `searchParam` alias. The global choice and property map may be omitted.
An omitted or empty search yields no user predicate when the property map is
empty. A valid global choice alone does not create a filter. Apply authorization
scopes even when the user predicate is absent.

The binder accepts `application/json` and `application/*+json` on non-GET requests.
Declare the endpoint's `consumes` types accordingly. Do not put reserved search
or case parameters in the URL as well as the JSON body: mixed sources are
rejected instead of silently merged. Ordinary pagination/sorting parameters may
remain in the URL. The default resolver uses the application's Jackson 3
`JsonMapper` when available, including stream constraints; duplicate-key and
trailing-document rejection are always enabled. Directly constructed resolvers
can receive a mapper through `new SearchSpecificationResolver(mapper)`.

## Programmatic construction

Core options are immutable and framework-independent. They do not change the
query tree or concatenate/reparse user values:

```java
SearchOptions options = new SearchOptions(CaseSensitivity.INSENSITIVE,
        Map.of("code", CaseSensitivity.SENSITIVE));
Query query = new Comparison("name", ComparisonOperator.EQUALS, nameInput)
        .and(new Comparison("code", ComparisonOperator.EQUALS, codeInput));

Specification<User> predicate = new JpaQueryTranslator<User>(settings, options)
        .translate(query);
```

`CaseSensitivity`, `SearchOptions`, `Query`, `Comparison` and `ComparisonOperator`
are in `com.pembana.search.core`; `JpaQueryTranslator` is in `com.pembana.search`.
`settings` is the application's `SearchSpec` policy, as shown in the
[direct JPA example](../getting-started.md#direct-jpa-usage-without-mvc).
The same options overload is available on `CriteriaParser` and
`SpecificationsBuilder` for both `withSearch` and `withQuery`. Existing one-argument
constructors use `SearchOptions.defaults()` and retain existing semantics.
Use `CaseSensitivity.wireValue()` when a Java client builds the lowercase wire
format; the enum's uppercase `name()` is not a valid request value.

## Validation and supported properties

Invalid options produce a 400-compatible `InvalidSearchQueryException` with code
`INVALID_SEARCH_OPTIONS`; known offending property paths appear as `field` in
ProblemDetail metadata. HTTP rendering remains application-owned. Unknown entity
paths can produce `UNKNOWN_FIELD` during JPA resolution; field access restrictions
retain `FIELD_NOT_ALLOWED`. Syntax and complexity failures retain their own codes.

The binder/translator reject:

- Values other than the exact lowercase `sensitive`, `insensitive`, `inherit` strings.
- Repeated reserved GET parameters, including duplicate expression parameters.
- Duplicate JSON keys, trailing documents, malformed/empty JSON and non-object roots.
- JSON nulls, booleans, numbers or arrays in place of required strings/property maps.
- Unknown JSON envelope members, malformed GET brackets or empty property keys.
- Property-map keys absent from the query, even if they select `inherit`.
- Nonempty property maps with an omitted/empty search.
- Explicit property choices on non-String attributes, including numeric, enum,
  Boolean, temporal and collection-valued properties.

Send property choices only for active query properties, using exact Java/entity
paths rather than SQL column names or UI labels. Type validation occurs when JPA
constructs a predicate because core parsing has no entity schema. A request-wide
choice applies only to String attributes; typed converters are unchanged. An
`inherit` entry on a non-text query property is a no-op rather than an explicit
case override. `allowedFields`/blacklists still control searchable fields; case
options never authorize a field or replace mandatory row-level restrictions.

## String operators and compatibility

On the tested default H2/PostgreSQL configurations, for a stored `Ada`:

| Expression | Explicit sensitive | Explicit insensitive |
| --- | --- | --- |
| `name:ADA` | No match | Match |
| `name!ADA` | Match | No match |
| `name:*ADA*` | No match | Match |
| `name IN [ADA]` | No match | Match |

Explicit choices consistently govern equality/inequality, patterns/negated
patterns, membership/negated membership and all four ordering operators. Inclusive
ranges and NOT BETWEEN expansion resolve the same property policy at both bounds.
String equality never adds implicit wildcards.

When both option levels inherit, the annotation's existing behavior is preserved:
false folds equality, inequality and patterns, but membership uses original
field/items and less-than ordering retains its original-field/folded-value
asymmetry. Use an explicit `insensitive` request/property choice to opt into
consistent membership/ordering. The underlying grammar and existing query model
are unchanged; no new expression operators or field-name decorations are added.

SQL nulls are distinct from literal `"null"` text. Inequality/NOT IN retain SQL
null semantics; use `IS NULL` or an OR with it when null rows should match. Empty
membership retains provider semantics. `IS NULL`, `IS NOT NULL` and collection
emptiness checks are unchanged. Enum, numeric, UUID, Boolean and temporal
conversion remain independent of text case choices.

Java values normalize with `Locale.ROOT`; SQL lower and comparison/collation rules
remain database-controlled. This is not accent removal, transliteration, fuzzy
matching or universal Unicode case folding. A sensitive choice skips explicit
folding but cannot force binary case sensitivity on an insensitive database
column/type/collation. Verify production collations and query plans; expression
indexes may be appropriate. SQL LIKE `%`/`_`, internal `*` interpretation and
association joins retain the [query reference](query-language.md) behavior.

## Suggested form UX

Default ordinary form text to insensitive matching and offer an advanced global
toggle. Per text property, show **Use global**, **Ignore case**, **Match case**
and its effective setting. Clearing a property choice should omit its map entry
or send `inherit`; omit entries for inactive query properties. Keep omitted
choices distinct from explicit insensitive choices. Numeric/date controls do not
need a case toggle; enum conversion stays exact.

The library implements these matching choices, not a form UI or a server-side
per-field defaults registry. Applications choose their configured fallback and
may initialize explicit property selections for identifiers. If a field must
always use a hard policy, validate/enforce that in application-owned configuration
or a replacement resolver; a client override is not an authorization rule. The
existing named resolver replacement remains available. Compose mandatory scopes
outside the complete user expression, as in the JSON example above.

## Migration and verification

Existing endpoints need no change to preserve query/form binding. JSON search
endpoints opt in with `@SearchRequestBody`. A false annotation flag continues to
honor the earlier equality/inequality correction; first-class explicit options
add consistent membership/ordering without rewriting historical baseline behavior.
No artifacts are published and the version remains `1.0.0-SNAPSHOT`.
See [options verification](../case-options/README.md) and the earlier
[equality-fix report](../case-sensitivity/README.md) for acceptance evidence.
