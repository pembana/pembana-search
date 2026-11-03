# Keep matches when an optional relation is missing

A book can have a title without having an author. When you search both fields,
a missing author should not hide a book whose title matches.

The optional-to-one policy lets you choose that behavior for an individual query.
A **to-one relation** means that each record points to at most one related record,
such as a book's author or cover. The policy supports JPA `@ManyToOne` and
`@OneToOne` mappings.

## The problem it solves

Consider these books:

| Title | Author name |
| --- | --- |
| Written | Writer |
| Orphan | No author assigned |
| Unrelated | No author assigned |

You search with this `STANDARD` expression:

```text
author.name:Writer OR title:Orphan
```

The existing behavior uses implicit inner-join navigation for the author path.
That can remove books without authors before the database evaluates the `OR`.
The result includes **Written**, while **Orphan** is lost.

With the optional-to-one policy enabled, the result includes **Written** and
**Orphan**. **Unrelated** still does not match: a missing author does not count
as the name `Writer`, and its title does not match either.

> **Recommended for new search endpoints:** Enable this policy when your queries
> use the [supported paths](#supported-paths). It usually matches user expectations:
> a matching title should return the book even when its author is missing.
> The library keeps it opt-in to preserve existing applications' results. Enabling
> it can change result counts and explicit null-check behavior, so review those
> differences when adopting it in an existing endpoint. The current path limits,
> including associations inside embedded objects, also need to be addressed before
> considering it as a library-wide default.

## Enable it for one query

Build a reusable extension configuration and pass it to the query that needs it:

```java
import org.springframework.data.jpa.domain.Specification;

import com.pembana.search.QueryExtensions;
import com.pembana.search.SpecificationsBuilder;

var optionalRelations = QueryExtensions.builder()
    .optionalToOne(true)
    .build();

var builder = new SpecificationsBuilder<Book>(searchSpec);
Specification<Book> search = builder
    .withExtendedSearch("author.name:Writer OR title:Orphan", optionalRelations)
    .build();
```

`Book` is your mapped entity, with an optional `author` relation and a `title`
property. `searchSpec` is your existing `@SearchSpec` configuration; see
[configuration](configuration.md). Pass the resulting specification to your
repository as usual.

The policy uses **left joins** when following to-one relations. These keep records
with a missing relation available for another matching branch of the query.
It changes path navigation only; it does not enable strict validation or select
a different case policy.

The configuration applies to this call only. A later ordinary call uses the
existing behavior:

```java
builder.withSearch("author.name:Writer OR title:Orphan").build();
```

You can also build an explicitly disabled configuration with
`QueryExtensions.builder().optionalToOne(false).build()`. The default is `false`.
A built configuration is immutable, so you can reuse it across requests. Use a
separate `SpecificationsBuilder` for each request.

## Use it with profiles or programmatic queries

Select a field profile and the policy together:

```java
builder.withExtendedSearch(input, publicProfile, optionalRelations).build();
```

For example, a profile can expose `writer` as an alias for `author.name`. The same
policy then applies to `writer:Writer OR title:Orphan`. Profile permissions and
canonical field allow/deny rules are checked before building the database paths.
Case choices and registered value converters keep their normal precedence.

Programmatic queries use the same translation path:

```java
builder.withExtendedQuery(query, optionalRelations).build();
builder.withExtendedQuery(query, publicProfile, optionalRelations).build();
```

The `withExtendedSearch` methods always parse `STANDARD` syntax. `SIMPLE` search
already uses left joins for its supported to-one paths, without this policy. See
[search modes](plain-text.md) and
[query extensions](extensions.md) for those APIs.

## How missing values behave

Keeping a book available for another `OR` branch does not invent an author name.
Ordinary comparisons retain SQL null behavior:

| Condition | What happens when the book has no author? |
| --- | --- |
| `author.name:Writer` | Does not match. |
| `author.name!Writer` | Does not match; a missing name is not a known different name. |
| `author.name IS NULL` | Matches. It also matches an assigned author whose name is null. |
| `author.name IS NOT NULL` | Does not match. |
| `author IS NULL` | Matches only when the author relation itself is missing. |

Negating an equality or pattern also keeps SQL null rules. If you want missing
values included, request that explicitly with `IS NULL` and the appropriate
Boolean grouping. `IN`, `NOT IN`, ranges, and existing empty-list behavior retain
their usual matching rules. The policy preserves the query's AND/OR/NOT grouping.

Nested to-one paths are supported too. For example,
`author.publisher.name:Press OR title:Orphan` can still find **Orphan** when either
the author or publisher is missing. Every traversed to-one link uses a left join,
including required links after an optional parent.

## Supported paths

| Path or mapping | Support |
| --- | --- |
| `author.name` | Many-to-one navigation. |
| `cover.label` or `review.label` | Owning and inverse one-to-one navigation. |
| `author.publisher.name` | Chains of to-one relations. |
| Inherited or getter-mapped properties | Resolved through JPA metadata, using the mapped property name. |
| `extra.caption` or `author.contact.city` | Embedded scalar properties, including after a to-one relation. |
| `tags IS EMPTY` or `author.books IS NOT EMPTY` | Existing terminal collection emptiness checks. |

A collection may be the final field of an explicit emptiness check. For
`author.books IS EMPTY`, a missing author has no matching books and satisfies the
emptiness check; `IS NOT EMPTY` requires books. This does not enable traversal into
collection children. Paths such as `author.books.title` require the separate
[collection policy](collections.md), selected with `collections(true)`.

To-one navigation after an embedded attribute, such as
`author.contact.region.name` where `contact` is embedded and `region` is an
association, is outside this increment and is also rejected explicitly. Unknown
mapped properties report `UNKNOWN_FIELD` when this policy is selected. Errors
include the full field path.

## Counts, pages, and application restrictions

Matching happens in the database before counting or pagination. The policy adds
regular left joins, creates no fetch joins, and leaves sorting to the caller.
It does not filter or deduplicate an already fetched page. Tests compare unpaged
results, counts, and multiple sorted pages on H2 and PostgreSQL.

Keep tenant, publication, and other required restrictions around the complete user
search:

```java
Specification<Book> effective = tenantScope.and(search);
```

The policy creates its own query-local joins and does not reuse or modify joins
created by your application. It also cannot undo an inner join that another
specification or a custom factory introduces. No roots, joins, or paths are cached
between result queries, count queries, or requests.

## Writing your own predicate factory

You can combine `optionalToOne(true)` with converters, transformations, and factories
in the same `QueryExtensions` builder. The library applies the policy to paths it
resolves itself, including ordinary fallback after a factory declines.

Inside a custom factory, use the public `ToOnePaths` helper when you want the same
path behavior:

```java
import jakarta.persistence.criteria.Path;
import com.pembana.search.ToOnePaths;

Path<?> authorName = ToOnePaths.resolve(context.root(), "author.name");
```

Use that path to build your condition with the factory's current CriteriaBuilder
and converted value. The helper follows the supported paths described above and
creates left joins for traversed to-one links. It works only with the current query
root; do not save the returned path for another execution.

Calling this helper is an explicit choice inside your factory. It does not apply
field permissions itself. Your factory remains responsible for which fields it
uses and for honoring the query's matching policy, including case choices. See
[predicate factories](extensions.md#build-a-custom-database-condition) for a
complete factory example.
