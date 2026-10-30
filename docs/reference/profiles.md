# Per-query field profiles

`SearchProfile` is an immutable, named policy selected explicitly for one query.
It defines public field names, canonical entity targets, permitted operations,
optional default SIMPLE fields, and case defaults. No profile is selected by
existing constructors, annotations, configuration, or classpath discovery.

```java
var publicProfile = new SearchProfile(
        "public-catalogue",
        Map.of("name", new SearchField("title", Set.of(
                ProfileOperation.EQUALS,
                ProfileOperation.CONTAINS,
                ProfileOperation.GLOB))),
        Map.of("label", "name"),
        List.of("name"),
        new SearchOptions(CaseSensitivity.INSENSITIVE, Map.of()));

// Each call explicitly selects its profile.
var builder = new SpecificationsBuilder<Book>(searchSpec);
Specification<Book> expression = builder
        .withInput("label:Sabal", SearchMode.STANDARD, publicProfile).build();
Specification<Book> text = builder
        .withInput("minor sabal", SearchMode.SIMPLE, publicProfile).build();
Specification<Book> programmatic = builder
        .withQuery(new Comparison("name", ComparisonOperator.EQUALS, "Sabal"),
                publicProfile).build();

// A call without a profile resumes ordinary canonical field handling.
Specification<Book> ordinary = builder.withSearch("title:Orphan").build();
```

Profile types are in `com.pembana.search.core`: `SearchProfile`, `SearchField`,
and `ProfileOperation`. Existing query, mode and case-option types stay in that
package. `SpecificationsBuilder` and `JpaQueryTranslator` are in
`com.pembana.search`. Imports for `Map`, `List`, and `Set` come from `java.util`.
The example's `Book` and `searchSpec` belong to the consumer.

Use `withSearch(expression, profile)` for explicit STANDARD syntax,
`withInput(input, mode, profile)` for mode selection, and `withQuery(query, profile)`
for programmatic construction. `INHERIT` resolves the consumer's existing mode
default. Each method replaces the previous specification. Profiles are never
retained as the default for later calls. Calling an existing overload without a
profile deactivates profile resolution; its input must use the ordinary canonical
field syntax. An unknown alias is rejected, with no fallback to another profile.
Direct adapter users can call `JpaQueryTranslator.translate(query, profile)`.
HTTP profile selection remains application-owned; no request parameter or annotation
member is added.

## Fields, aliases and permissions

The field map explicitly exposes public names. Each `SearchField` holds a canonical
property path and an immutable set of allowed operations. The alias map points
additional public names at registered fields or other aliases. It inherits the
resolved field's permissions. Canonical paths are accepted as input only when
explicitly exposed as public names. For example, the profile above accepts `name`
and `label` but rejects direct `title` input. To expose `title`, add an alias
`title -> name`.

Construction defensively copies all maps, lists and sets. Blank profile names,
invalid public names/paths, public-name collisions, cycles, unresolved alias links,
and duplicate canonical SIMPLE targets are configuration errors. Public names use
the existing expression identifier alphabet: ASCII letters, digits and dots.
Canonical paths consist of nonempty alphanumeric segments separated by dots.
Entity existence and attribute types are checked by the existing JPA translation
and strict-validation policy at predicate construction, since profiles have no
entity metadata dependency.

After alias resolution, the existing canonical allowlist/blacklist is applied;
blacklist precedence remains. Profiles cannot authorize a denied canonical path.
A field with an empty operation set permits no comparisons. `ProfileOperation`
contains the existing comparison names, plus `IN`, `NOT_IN`, and `GLOB`.
Membership checks its corresponding permission. Range expressions expand to the
existing inclusive comparison nodes, and both bounds must be permitted. Grouping
and negation retain their existing query-tree representation. Profiles validate
operations on those nodes and leave Boolean structure intact.

SIMPLE word queries require `CONTAINS`; star patterns require `GLOB`. Nonempty
profile text fields replace the consumer's configured SIMPLE fields. An empty
profile list uses the consumer's configured fields, interpreted as public names
through the same profile. SIMPLE targets must remain root String attributes;
STANDARD paths retain the existing association behavior. Selecting a profile
does not enable new joins or collection semantics.

Unknown public names and profile-disallowed operations use `FIELD_NOT_ALLOWED`,
with the public field and profile name in the reason. Existing canonical access
failures retain their ordinary diagnostics. Conflicting case options use
`INVALID_SEARCH_OPTIONS`. Malformed STANDARD input continues to fail as syntax,
without automatic mode fallback. Core use exposes `QueryParseException`; the JPA
adapter translates those diagnostics through `InvalidSearchQueryException`.

## Case defaults and isolation

Pass request case choices through the builder's existing `SearchOptions`.
Profile defaults and request property keys use the profile's public-name mapping.
Two names resolving to the same target must agree, including explicit `INHERIT`
entries; conflicting choices are rejected independently of map iteration order.
Request property keys must resolve to fields used in that query. Profile defaults
for unused fields are omitted rather than causing unused-option errors.

Precedence is: explicit request property choice, explicit request global choice,
profile property default, profile global default, then the existing annotation
fallback for STANDARD (or the insensitive preset fallback for SIMPLE). An
`INHERIT` value advances to the next applicable choice. Explicit property choices
require String attributes, including profile property defaults. Global choices
continue to ignore numeric and other non-String attributes. Unresolved STANDARD
`INHERIT` remains intact to preserve legacy case behavior.

Profiles can be reused concurrently by independent consumers. Builders remain
request-local, and JPA query objects are created only during predicate execution.
Mandatory tenant/publication constraints remain caller-owned and should enclose
the complete user search, for example `tenantScope.and(userSearch)`.

Profiles support ordinary entity-path mappings and logical fields backed by
explicitly selected [query extensions](extensions.md). Registered virtual-field
factories and transformations still require profile permissions; transformed
canonical targets must be exposed by the same profile.
