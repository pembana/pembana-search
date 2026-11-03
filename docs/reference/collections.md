# Search records by their collection contents

A product can have several variants, each with a color, a price, and an optional
supplier. Collection handling lets you find a product when one of those variants
matches your search. It also supports simple collections such as a set of labels.

Enable it only for the queries that need it. Existing search behavior stays the
default, and selecting a field profile does not enable collection handling.

## Enable it for one query

Build a reusable configuration, then pass it to an extended search call:

```java
import org.springframework.data.jpa.domain.Specification;

import com.pembana.search.QueryExtensions;
import com.pembana.search.SpecificationsBuilder;

var collectionSearch = QueryExtensions.builder()
    .collections(true)
    .build();

var builder = new SpecificationsBuilder<Product>(searchSpec);
Specification<Product> search = builder
    .withExtendedSearch("variants.color:red", collectionSearch)
    .build();
```

`Product` is your mapped entity, `variants` is its collection, and `color` is a
property of each variant. `searchSpec` is your existing `@SearchSpec` configuration;
see [configuration](configuration.md). Pass the result to your repository as usual.
A product with several red variants appears once in the results.

A built configuration is immutable and can be shared across requests. Use a new
`SpecificationsBuilder` for each request. Each call replaces its previous search;
a later `builder.withSearch(input)` uses ordinary behavior again. The default for
`collections` is `false`; you can also disable it explicitly with
`QueryExtensions.builder().collections(false).build()`.

You can select a [field profile](profiles.md) at the same time:

```java
builder.withExtendedSearch(input, publicProfile, collectionSearch).build();
builder.withExtendedQuery(query, publicProfile, collectionSearch).build();
```

These methods accept `STANDARD` expressions or programmatic `Query` objects.
`SIMPLE` search supports configured collection String fields directly, without
this toggle; see [plain-text search](plain-text.md). If your endpoint exposes a
request toggle, application code chooses when to pass the registry; the library
does not add an HTTP parameter.

## Match a child, or require that none match

Suppose these products have the following variant colors:

| Product | Variant colors |
| --- | --- |
| Empty | No variants |
| Red | red |
| Blue | blue |
| Mixed | red, blue |
| Unnamed | One variant whose color is null |

With collection handling enabled:

| Search | Matching products | Meaning |
| --- | --- | --- |
| `variants.color:red` | Red, Mixed | At least one variant is red. |
| `variants.color IN [red,blue]` | Red, Blue, Mixed | At least one variant has either color. |
| `variants.color!red` | Empty, Blue, Unnamed | No variant is red. |
| `variants.color NOT IN [red,blue]` | Empty, Unnamed | No variant has either color. |
| `variants IS EMPTY` | Empty | There are no variants. |
| `variants IS NOT EMPTY` | Red, Blue, Mixed, Unnamed | At least one variant exists. |
| `variants.color IS NULL` | Unnamed | An actual variant has a null color. |
| `variants.color IS NOT NULL` | Red, Blue, Mixed | An actual variant has a non-null color. |

The negative operators `!`, `!*text*`, `!text*`, `!*text`, and `NOT IN` mean **no
matching child exists**. Thus, Mixed does not match `variants.color!red`, even
though it also has a blue variant. Empty collections do match these absence checks.
An empty `IN []` matches nothing; `NOT IN []` matches every product.

A null property is different from an empty collection: `variants.color IS NULL`
requires an actual variant. To negate that entire condition programmatically, use
`query.not()`; the resulting search includes products with no variants.

For basic collections, search the collection name directly. For example,
`labels:red` finds a product with a `String` label equal to `red`, while
`labels!red` finds one with no red label. Normal String patterns and case choices
apply to the elements.

## Decide whether conditions must match the same child

By default, separate conditions may match different children:

```text
variants.color:red AND variants.size:large
```

A product with a **small red** variant and a **large blue** variant matches this
search: it has a red variant and it has a large variant.

To require a **large red** variant, group both conditions in an explicit child
scope. `AssociationQuery` is an additional query model for expressing this choice:

```java
import com.pembana.search.core.AssociationQuery;
import com.pembana.search.core.Comparison;
import com.pembana.search.core.ComparisonOperator;

var largeRed = AssociationQuery.any("variants", AssociationQuery.where(
    new Comparison("variants.color", ComparisonOperator.EQUALS, "red")
        .and(new Comparison("variants.size", ComparisonOperator.EQUALS, "large"))));

Specification<Product> search = builder
    .withAssociationQuery(largeRed, collectionSearch)
    .build();
```

`any` means “there is a child satisfying this whole condition.” Use full field
paths inside it. With a profile, those fields may instead use its public aliases;
the scope name (`"variants"`) always uses the canonical entity path. Select the
profile with `withAssociationQuery(largeRed, publicProfile, collectionSearch)`.

Conditions within a scope apply to its one child. For example, a scoped
`NOT_EQUALS red` means that an actual child has a non-null color different from
red. Mixed then matches through its blue child. To ask for **no large red
variant**, negate the scope itself:

```java
builder.withAssociationQuery(largeRed.not(), collectionSearch).build();
```

You can combine scopes and root conditions using `and`, `or`, and `not`:

```java
var largeRedOrFeatured = largeRed.or(AssociationQuery.where(
    new Comparison("title", ComparisonOperator.EQUALS, "Featured")));
```

A scope's conditions must stay within its collection. Put root conditions outside
the scope, as above. Nested collection scopes are currently unsupported. There is
no additional text syntax for an explicit same-child group.

## Keep range bounds on one value

A parsed range always checks both bounds against the same child:

```text
variants.price BETWEEN 5 AND 10
```

A product with prices `2` and `20` does not match. A product with a price of `7`
does. `NOT BETWEEN` means no child has a price in that range, and includes products
with no variants.

For programmatic ranges, use:

```java
var affordable = AssociationQuery.between("variants.price", "5", "10");
builder.withAssociationQuery(affordable, collectionSearch).build();
```

The existing `Query` model remains unchanged. If you manually combine a
`GREATER_THAN_EQUALS` comparison and a `LESS_THAN_EQUALS` comparison, they remain
independent conditions and may match different children. Wrap them in `any` when
you want both to apply to one child, or use `AssociationQuery.between`.

## Follow optional relations inside a collection

A variant can exist without having a supplier. These queries still work:

```text
variants.supplier.name:Acme OR title:Featured
variants.supplier.name IS NULL
```

The first query finds a product with an Acme variant or the title Featured,
including a Featured product whose variants have no supplier—or no variants at
all. The second requires an actual variant whose supplier is missing or whose
supplier's name is null. `variants.supplier IS NULL` specifically checks that the
supplier relation itself is missing.

Collection handling uses left joins for to-one relations on the way to a collection
and inside each child. This includes chains such as
`variants.supplier.country.name` and paths such as `catalog.variants.color`.

For other to-one fields outside collection conditions, select the
[optional-to-one policy](optional-to-one.md) as well:

```java
var relationSearch = QueryExtensions.builder()
    .collections(true)
    .optionalToOne(true)
    .build();
```

For example, this configuration also preserves a matching title in
`manufacturer.name:Acme OR title:Featured` when the product has no manufacturer.
Each setting can be selected independently.

## Supported mappings and limits

Collection discovery follows JPA mapping metadata, including inherited attributes
and mappings declared on getters.

| Mapping or path | Supported behavior |
| --- | --- |
| `@OneToMany` | Search entity children. |
| `@ManyToMany` | Search either the owning or inverse side. |
| `@ElementCollection` of basic values | Search the element itself, such as `labels:red` or `scores>5`. |
| `@ElementCollection` of embeddables | Search element properties, such as `features.color:red`. |
| Lists and sets | Match contents without changing list order or result pagination. |
| Optional `@ManyToOne` / `@OneToOne` before or inside a collection | Follow traversed relations with left joins. |
| Embedded scalar properties inside children | Search properties such as `variants.details.caption`. |

Each supported path contains **one collection step**. Nested collections such as
`variants.reviews.rating`, map-valued collections, and collection navigation after
an embedded property are unsupported. To-one navigation after an embedded property,
such as `variants.details.supplier.name`, is also unsupported. These paths report
`UNSUPPORTED_OPERATION`; unknown fields report `UNKNOWN_FIELD`.

Existing type/operator validation and case policies still apply. Enable
`strictValidation` in your `@SearchSpec` to reject unsupported scalar operations
consistently. A child condition must produce a predicate; a legacy operation that
returns no predicate is rejected within a collection scope.

## Use converters, transformations, and custom factories

[Query extensions](extensions.md) work on canonical child paths too. For example,
register a converter for `variants.price` to accept a custom price format. Each
range bound and each `IN` item is converted separately. Profiles, operation
permissions, field restrictions, and case choices apply as usual.

Transformations retain the current scope. A transformation inside `any("variants",
...)` may produce conditions on variant fields, but cannot move a condition outside
that scope. A transformed range field requires an explicit `any` scope; an unscoped
`BETWEEN` on a registered transformation reports `UNSUPPORTED_OPERATION` because
its replacement might refer to several fields.

A factory for a child field receives the correlated parent root and a null
`context.query()`: its condition belongs to a subquery, which the existing
`CriteriaQuery` field cannot represent. Use `CollectionPaths.resolve` to address
the current child:

```java
import com.pembana.search.CollectionPaths;

var color = CollectionPaths.resolve(context.root(), "variants.color");
var predicate = context.builder().equal(color, context.value());
```

This also works for a virtual child name, such as a factory registered under
`variants.displayColor`. Resolve its real child attributes using the helper.
It follows optional to-one links within that scope and rejects use without an
active child scope. A factory registered on the collection itself for an `EMPTY`
check retains its ordinary parent-query context, because that check concerns the
whole collection. A declining factory falls back to ordinary translation on that
same child.

Factories receive the original requested operator, including negative operators.
Outside an explicit scope, the library wraps the resulting child predicate to
implement the absence semantics described above. A factory must implement the
supplied operation correctly and honor case choices. It remains responsible for
the fields it accesses. Keep all JPA objects local to the current execution.

## Counts, pages, and required application restrictions

Collection conditions use correlated `EXISTS` / `NOT EXISTS` subqueries. Matching
children stay out of the outer result rows, so several matching children do not
multiply a parent's result or inflate its count. Reusing the specification for a
count or a paged query creates fresh query state each time. The policy does not
add collection fetches or change your sorting. Joins introduced by your own
specifications or factories still affect the query.

Keep tenant, visibility, and other required restrictions around the complete user
search, including any `OR` branches:

```java
Specification<Product> effective = tenantScope.and(search);
```
