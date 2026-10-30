# Customize search with query extensions

Query extensions let your application accept special value formats, offer search
fields that are not stored on an entity, and turn one search condition into several.
For example, a user could enter `code:CODE-12` to find an item whose stored ID is
`12`, or `all:Ada` to search both its title and its note.

Choose the extension that fits the job:

| You want to… | Use… | Example |
| --- | --- | --- |
| Read a value in an application-specific format | A **converter** | Turn `CODE-12` into the Java `Long` value `12`. |
| Combine existing search conditions | A **transformation** | Turn `anyText:Ada` into `(title:Ada OR note:Ada)`. |
| Build a custom database condition | A **predicate factory** | Add a virtual `displayName` field that checks several entity properties. |

Add your extensions to a `QueryExtensions` registry—a collection of registrations
that you can reuse across requests. Select that registry for each query that needs
it. These APIs work with `STANDARD` expressions and programmatic `Query` objects.
For ordinary text input in `SIMPLE` mode, see [plain-text search](plain-text.md).

## Start with a custom value format

Suppose your `Item` entity has a `Long` property named `id`, and users recognize IDs
such as `CODE-12`. This converter accepts that format while also allowing plain
numeric input:

```java
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import com.pembana.search.QueryExtensions;
import com.pembana.search.SpecificationsBuilder;
import com.pembana.search.core.InvalidSearchValueException;
import com.pembana.search.core.ProfileOperation;
import com.pembana.search.core.ValueConversion;

var extensions = QueryExtensions.builder()
    .forField("id", new ValueConversion(Long.class,
        Set.of(ProfileOperation.EQUALS, ProfileOperation.IN), raw -> {
            if (!raw.startsWith("CODE-")) {
                // Let the next converter handle plain input such as "12".
                return Optional.empty();
            }
            try {
                return Optional.of(Long.valueOf(raw.substring(5)));
            }
            catch (NumberFormatException exception) {
                throw new InvalidSearchValueException("Expected a code such as CODE-12");
            }
        }))
    .build();

var builder = new SpecificationsBuilder<Item>(searchSpec);
Specification<Item> search = builder
    .withExtendedSearch("id IN [CODE-12,13]", extensions)
    .build();
```

`Item` is your application's mapped entity. `searchSpec` is its existing
`@SearchSpec` configuration; see [configuration](configuration.md) for the library's
entry points. The resulting specification can be passed to your repository.

For this registration:

| Input | Result |
| --- | --- |
| `id:CODE-12` | Compare `id` with the numeric value `12`. |
| `id:12` | Use built-in Long parsing and compare with `12`. |
| `id IN [CODE-12,13]` | Match ID `12` or `13`; convert each list item separately. |
| `id:CODE-abc` | Reject the input with `INVALID_VALUE`. |

The operation set declares what this converter supports. This example permits
equality and `IN`. If you need other operations, include them explicitly.

## Choose extensions for each query

Use the extended methods when you want a registry applied:

```java
builder.withExtendedSearch("id:CODE-12", extensions).build();
builder.withExtendedQuery(query, extensions).build();
```

You can select a [field profile](profiles.md) at the same time:

```java
builder.withExtendedSearch(input, profile, extensions).build();
builder.withExtendedQuery(query, profile, extensions).build();
```

Each call replaces the builder's previous search. Extensions and profiles apply to
that call only. A later ordinary call uses ordinary search behavior:

```java
builder.withSearch("id:12").build();
```

An empty registry also uses ordinary translation. A registration for another field
or type has no effect on the query. When a converter, transformation, or factory
returns `Optional.empty()`, it asks the library to continue through its fallback
steps. Errors from extensions remain errors; the library does not turn them into an
unrestricted search. If you stop using the registry, your application must decide
whether to continue accepting inputs such as `CODE-12`.

The library does not discover registrations from Spring beans or the classpath.
Your application chooses any HTTP parameters or endpoint rules for selecting them.
Create a builder per request; a built registry is immutable and can be shared.

## Search several fields with one condition

A transformation is useful when existing query operations already describe the
search you want. The following registration adds `anyText` as a search name and
expands a comparison into a grouped condition over `title` and `note`:

```java
var extensions = QueryExtensions.builder()
    .transform("anyText", criterion -> {
        if (criterion instanceof Comparison comparison) {
            return Optional.of(
                new Comparison("title", comparison.operator(), comparison.value())
                    .or(new Comparison("note", comparison.operator(), comparison.value())));
        }
        return Optional.empty();
    })
    .build();

builder.withExtendedSearch("anyText:Ada", extensions).build();
```

Here, `Comparison` is `com.pembana.search.core.Comparison`. The query means
`(title:Ada OR note:Ada)`. It uses equality, following `STANDARD` syntax. A pattern
such as `anyText:*Ada*` keeps its contains operation when expanded. This example
handles comparisons and leaves other kinds of conditions to the fallback path;
the [standalone example](#try-the-standalone-example) also handles `IN` conditions.

The library keeps the surrounding Boolean structure. For example,
`anyText:Ada AND id:12` becomes `(title:Ada OR note:Ada) AND id:12`.
Transformations build immutable query objects, so values do not need to be joined
into expression strings or parsed again.

With a profile, both the original condition and its replacement fields must be
permitted. Register extension names using their resolved entity paths, also called
**canonical paths**. For example, if a profile maps public `name` to entity `title`,
a converter for that property is registered under `title`. Transformation results
also use canonical paths. A profile may expose those targets through aliases;
it does not need to accept direct canonical field names as user input.

A property-specific case choice on the original condition is passed to its
replacement fields unless a request choice already exists for a target. Profile
case defaults for the target fields otherwise apply normally.

## Build a custom database condition

A **predicate** is a database condition that decides whether a row matches. A
predicate factory gives you control over that condition through the JPA Criteria
API. Use it when you need more than a combination of existing query operations.

A **virtual field** is a search name with no corresponding entity property. In
this example, `displayName:Ada` checks whether either `title` or `note` equals `Ada`.
The factory also respects the query's case settings:

```java
var extensions = QueryExtensions.builder()
    .factory("displayName", new PredicateRegistration(String.class,
        Set.of(ProfileOperation.EQUALS), context -> {
            boolean sensitive = switch (context.caseSensitivity()) {
                case SENSITIVE -> true;
                case INSENSITIVE -> false;
                case INHERIT -> context.legacyCaseSensitive();
            };
            String value = (String) Objects.requireNonNull(context.value());
            Expression<String> title = context.root().get("title");
            Expression<String> note = context.root().get("note");
            if (!sensitive) {
                value = value.toLowerCase(Locale.ROOT);
                title = context.builder().lower(title);
                note = context.builder().lower(note);
            }
            return Optional.of(context.builder().or(
                context.builder().equal(title, value),
                context.builder().equal(note, value)));
        }))
    .build();

builder.withExtendedSearch("displayName:Ada", extensions).build();
```

This snippet also uses `com.pembana.search.PredicateRegistration`,
`jakarta.persistence.criteria.Expression`, and `java.util.Objects`/`Locale`.
The transformation above can express the same search more briefly; this version
shows how to build a predicate yourself.

`String.class` tells the library what type of value to prepare for the factory.
`PredicateContext` supplies that converted value, the resolved field name,
permitted operation, case settings, and JPA objects for the current query. Its
`query()` can be null in some JPA calls, so check it before using it.
The entity's `title` and `note` properties are supplied by your application.
A factory can also customize a condition on an ordinary entity field.

To expose a virtual field through a profile, map a public name to it with
`new SearchField("displayName", permittedOperations)`. The profile and ordinary
field restrictions still apply to that name. The factory is responsible for the
entity fields it accesses when building its condition.

A factory can return `Optional.empty()` to request ordinary field translation.
Any accepted custom conversion still applies. If the virtual name has no entity
property, that fallback reports the usual unresolved-field error.

## Keep errors and application restrictions clear

Use `InvalidSearchValueException` for input the user can correct, with a message
safe to display to them. The library reports it as `INVALID_VALUE`. Other callback
exceptions propagate as application or infrastructure failures. Wrong converter
return types and incompatible registration types are also application errors.

A registration rejects operations outside its permitted set with
`UNSUPPORTED_OPERATION`. Profile and field permission failures keep their existing
error contracts. Your application remains responsible for rendering HTTP errors.

Factories run as application code with access to the persistence query. Pass user
values as data in Criteria predicates or bound SQL parameters. Keep tenant,
publication, and other mandatory restrictions around the complete user search:

```java
Specification<Item> effective = tenantScope.and(userSearch);
```

Your converter, transformation, and factory functions must be safe to share across
request threads. Use the JPA context only
for the current predicate call; do not store roots, queries, joins, subqueries, or
predicates for reuse. The registry copies its configuration, but cannot make a
callback's captured mutable state safe for concurrent use.

## Reference details

### Conversion order and supported types

For each scalar value, list item, or expanded range bound, conversion proceeds in
this order:

1. A converter registered with `forField`, if one matches the canonical field.
2. A converter registered with `forType`, if one matches the exact Java type.
3. The built-in converter.

Returning `Optional.empty()` continues to the next step. Once a converter accepts
a value, later converters are skipped for that value. Allowed operations are
checked before invoking a converter. Each extension function must return an `Optional`,
never null.

An ordinary field converter's declared type must match the entity attribute.
For a virtual field, the converter and factory declare the same logical value type.
Type registrations match exact Java classes; primitive and boxed types are separate
keys. Returned values must match the declared type, using boxed values for primitives.
Duplicate registrations at the same level are rejected while building the registry.

A converter can add support for a persistent type that ordinary strict validation
does not support, such as `BigDecimal`. Declare its permitted operations explicitly.
Equality and `IN` bind the converted values directly; ordering needs `Comparable`
values. Your entity mapping and persistence provider must support that type.
String conversions keep the existing string matching and case rules.

An `IN` list can mix custom and built-in value formats. Empty lists keep the usual
`IN`/`NOT IN` behavior and do not call scalar converters. Explicit `NULL` and `EMPTY`
checks also skip scalar conversion. A factory receives null for a `NULL` check and
the state token for an `EMPTY` check.

Factories that implement string matching should use the supplied case choice.
For `INHERIT`, `legacyCaseSensitive()` gives the annotation's fallback. Global text
case choices do not alter non-String values; explicit property case choices require
String values.

### Transformation order and limits

For each condition, the library checks the original field and operation, applies
any transformation, then checks each replacement field and operation. Conversion
and predicate creation follow those checks. A replacement cannot gain access to a
field that the selected profile or canonical field policy forbids. Factories remain
responsible for the conditions they construct themselves.

By default, transformations may visit up to 1024 query nodes per query, with a
nesting depth of 64. A node is a condition or a Boolean group. To change them,
use `QueryExtensions.builder().limits(nodes, depth)`. Both values must be positive,
and depth cannot exceed 256. Cycles and exceeded limits report
`QUERY_LIMIT_EXCEEDED`. A query with no matching transformation does not use
that budget. The ordinary expression length and grouping limits still apply to
submitted input.

### Direct translation and API locations

If you do not use `SpecificationsBuilder`, construct
`new ExtendedQueryTranslator<T>(settings, options, extensions, profile)` and call
`parse` for a `STANDARD` expression or `translate` for a programmatic query.
`profile` may be null. You can use these APIs without a Spring context.

| Package | Types |
| --- | --- |
| `com.pembana.search.core` | `ValueConversion`, `ValueConverter`, `CriterionTransformer`, `InvalidSearchValueException`, `ProfileOperation`, and query-model types |
| `com.pembana.search` | `QueryExtensions`, `PredicateFactory`, `PredicateRegistration`, `PredicateContext`, `ExtendedQueryTranslator`, `SpecificationsBuilder` |

## Try the standalone example

[ExtensionConsumer.java](../../pembana-search-data-jpa/src/test/java/com/pembana/search/samples/ExtensionConsumer.java)
combines the three extension types in one small example. It expects an entity with
`id` (`Long`), `title`, and `note` (`String`) attributes. The sample lives in the test
sources; copy or adapt it for your application. It uses public APIs and has no
application entity or Spring context dependency.

Its profile provides these search names:

| Input | What it does |
| --- | --- |
| `code:CODE-12` | Convert the display code to `12`, then compare the entity's `id`. |
| `code:12` | Use built-in Long parsing, then compare `id` with `12`. |
| `all:Ada` | Search `(title:Ada OR note:Ada)` through a grouped transformation. |
| `all IN [Ada,Beta]` | Match either value in `title` or `note`. |
| `name:Ada` | Compare the entity's `title` directly with `Ada`. |

Call `ExtensionConsumer.scoped(searchSpec, requiredScope, input)` to build the user
search and combine it with your required restriction. You can also reuse
`registrations()` separately when selecting extensions yourself. The
`QueryExtensionsTest.standaloneConsumerExample` test runs this sample against the
suite's H2 and PostgreSQL profiles, including checks that the user query stays
inside the required scope.

Extensions currently apply to `STANDARD` and programmatic queries. `SIMPLE` uses
its root-String pattern adapter. For optional `@ManyToOne` and `@OneToOne` relations,
see [optional to-one handling](optional-to-one.md). Select it per query with
`optionalToOne(true)` on the same extension builder. Collection matching policies
remain a separate feature.
