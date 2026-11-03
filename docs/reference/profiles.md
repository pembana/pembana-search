# Per-query field profiles

`SearchProfile` is an immutable, named policy selected explicitly for one query.
It defines public field names, canonical entity targets, permitted operations,
optional default SIMPLE fields, and case defaults. Select a profile explicitly
through the Java API or an HTTP endpoint annotation. Registering a profile alone
does not apply it to any query.

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
HTTP endpoints can select registered profiles with `@SearchSpec(profile = "public-catalogue")`.
See the [HTTP walkthrough](#use-a-profile-in-an-http-endpoint).

## Use a profile in an HTTP endpoint

A profile is your endpoint's list of public search fields and allowed operations.
For example, your entity may store a book's name in `title`, while API users
search for `name`. A profile connects those two names and can reject fields or
operations you do not want to expose.

### 1. Register a reusable profile

With the Spring Boot starter, declare a `SearchProfile` bean in your application:

```java
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.pembana.search.core.CaseSensitivity;
import com.pembana.search.core.ProfileOperation;
import com.pembana.search.core.SearchField;
import com.pembana.search.core.SearchOptions;
import com.pembana.search.core.SearchProfile;

@Configuration
class BookSearchConfiguration {
    @Bean
    SearchProfile bookSearchProfile() {
        return new SearchProfile(
            "public-catalogue",
            Map.of("name", new SearchField("title", Set.of(
                ProfileOperation.EQUALS, ProfileOperation.CONTAINS))),
            Map.of("label", "name"),
            List.of(),
            new SearchOptions(CaseSensitivity.INSENSITIVE, Map.of()));
    }
}
```

Here, `name` is the public field, `title` is the entity property, and `label` is
another public name for the same field. The profile allows equality and contains
searches and defaults to ignoring case. The empty list supplies no plain-text
fields; HTTP profile binding continues to use advanced expressions.

The registration key is **`public-catalogue`**, the first constructor argument.
It is not the Spring bean method name `bookSearchProfile`. Multiple endpoints
can share a profile; multiple profiles must have unique profile names.

### 2. Select the profile on an endpoint

Your controller can use the profile without manually building specifications:

```java
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.GetMapping;
import com.pembana.search.annotation.SearchSpec;

@GetMapping("/books")
List<Book> books(
        @SearchSpec(profile = "public-catalogue") @Nullable Specification<Book> search) {
    return search == null ? repository.findAll() : repository.findAll(search);
}
```

This method belongs inside your application's controller. `Book` is your entity
with a String `title` property. `repository` is your application-owned repository
implementing `JpaRepository<Book, Long>` and `JpaSpecificationExecutor<Book>`.
The null check handles an omitted or exactly empty search. If you apply mandatory
restrictions such as a tenant scope, combine them around the complete user search.

### 3. Try some searches

```sh
curl --get --data-urlencode 'search=name:ADA' http://localhost:8080/books
curl --get --data-urlencode 'search=label:*ada*' http://localhost:8080/books
```

| Search | Result |
| --- | --- |
| `name:ADA` | Compares `title`, ignoring case. |
| `label:*ada*` | Searches for `ada` anywhere in `title`, ignoring case. |
| `title:Ada` | Rejected: the canonical entity name was not exposed publicly. |
| `name>Ada` | Rejected: this profile does not permit ordering. |
| `missing:Ada` | Rejected: the public field is not registered. |

Permission failures use `FIELD_NOT_ALLOWED`. To request sensitive matching for
one search, send `fieldCaseSensitivity[name]=sensitive`; option keys use public
names or aliases. Explicit request choices override profile defaults.

### GET, forms, and JSON use the same profile

`searchParam` still changes the query/form parameter name, for example
`@SearchSpec(profile = "public-catalogue", searchParam = "q")` reads `q=name:Ada`.
Form endpoints use the same annotation on their `Specification` parameter.
For JSON, add `@SearchRequestBody` from `com.pembana.search.configuration`:

```java
@PostMapping("/books/search")
List<Book> searchBooks(
        @SearchRequestBody @SearchSpec(profile = "public-catalogue")
        @Nullable Specification<Book> search) {
    return search == null ? repository.findAll() : repository.findAll(search);
}
```

Import `PostMapping` from `org.springframework.web.bind.annotation` for this method.
The JSON envelope always uses `search`, independently of `searchParam`:

```json
{
  "search": "label:ADA",
  "fieldCaseSensitivity": {"label": "sensitive"}
}
```

The server selects the profile in the annotation. There is no client-selectable
profile parameter or JSON member. Each endpoint keeps its own profile; a selected
profile does not affect other endpoints. An annotation with an empty `profile`
(the default) keeps ordinary canonical-field handling, even when profile beans
are registered.

### Configuration, restrictions, and custom resolvers

Duplicate profile names fail resolver creation during Boot startup. A missing or
misspelled selected profile reports an application configuration error when that
endpoint is resolved, including when no search was supplied. It never falls back
to unrestricted search. This is a server configuration problem, not an invalid
user search. Correct the bean registration or annotation name.

Existing `allowedFields` and `blackListedFields` still use **canonical** paths
such as `title`; the profile's public names are resolved first. Profile operation
permissions, strict validation, input limits, and case-option validation all
continue to apply. A profile does not enable collection policies or query
extensions, and this feature does not add HTTP selection of SIMPLE mode.

Outside Boot, or when creating your own resolver, supply profiles explicitly:
`new SearchSpecificationResolver(mapper, List.of(publicProfile))`. Existing
no-argument and mapper-only constructors register no profiles. A replacement
resolver bean owns its own profile integration. Direct Java builders continue
to select profiles using their existing overloads; the annotation's `profile`
member is interpreted by HTTP binding only.

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
through the same profile. SIMPLE targets may be supported nested String paths or
collection String elements;
STANDARD paths retain the existing association behavior unless the query also
selects [optional to-one handling](optional-to-one.md) or
[collection handling](collections.md). For STANDARD queries, selecting a profile
alone
does not enable new joins or collection semantics. See [plain-text search](plain-text.md)
for SIMPLE path support and collection word matching.

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
`INHERIT` uses the annotation fallback consistently across String operations.

Profiles can be reused concurrently by independent consumers. Builders remain
request-local, and JPA query objects are created only during predicate execution.
Mandatory tenant/publication constraints remain caller-owned and should enclose
the complete user search, for example `tenantScope.and(userSearch)`.

Profiles support ordinary entity-path mappings and logical fields backed by
explicitly selected [query extensions](extensions.md). Registered virtual-field
factories and transformations still require profile permissions; transformed
canonical targets must be exposed by the same profile.
