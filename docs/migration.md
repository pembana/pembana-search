# Migrating to Pembana Search

This repository is a Java fork of Spring Search, not a drop-in binary replacement.
Its initial release is still under development; no Maven Central availability or
namespace/publishing-account registration is claimed.

| Previous import/build identity | Current identity |
| --- | --- |
| `com.sipios:spring-search:0.3.0` | `com.pembana:pembana-search-data-jpa:1.0.0-SNAPSHOT`, or `pembana-search-spring-boot-starter` for MVC |
| `com.sipios.springsearch.*` | `com.pembana.search.*` |
| `com.sipios.springsearch.anotation.SearchSpec` | `com.pembana.search.annotation.SearchSpec` |
| `SpringSearchApplication` test fixture | `PembanaSearchTestApplication` |
| `SpringSearchApplicationTest` corpus | `PembanaSearchCompatibilityTest` |
| Default-package deprecated `SyntaxErrorListener` | Removed; use `com.pembana.search.listeners.SyntaxErrorListener` |

Replace the dependency and imports together. There are no old-package aliases,
relocation artifacts, or binary compatibility shims. Remove the old artifact
from application dependencies to avoid retaining both annotation/resolver types.
Update fully qualified names in reflection, explicit imports, auto-configuration
exclusions and tooling. The Boot imports resource now names the Pembana class.
Normal Boot discovery does not require consumer component scanning.

## Migrating the single snapshot to modules

The earlier `com.pembana:pembana-search:1.0.0-SNAPSHOT` JAR becomes the
parent/aggregator POM. Replace that application dependency with
`pembana-search-spring-boot-starter` for Boot MVC/JPA integration, or
`pembana-search-data-jpa` for direct JPA. Use `pembana-search-core` alone for
Spring-independent parsing/model construction. All retain group `com.pembana`
and version `1.0.0-SNAPSHOT`; nothing is published and there is no relocation JAR.
Existing Java JPA/MVC imports remain available through their respective modules.

`CriteriaParser` now parses an immutable core tree before JPA translation. The
same translator accepts programmatic trees without concatenating query strings.
The module split preserved conversion rules, grammar, negative cases and error
metadata. The subsequent [Boolean precedence correction](#boolean-operator-precedence-correction)
changes mixed expressions without parentheses. Plain-text search remains opt-in.
The one moved HTTP characterization method and all retained scenario identities
are mapped in the [module report](modules/README.md).

## Changes already made before this rename

- Handwritten production/tests moved from Kotlin to Java. Original scenarios are retained.
- The tested framework baseline moved to Spring Boot 4.1.1 with Hibernate 7;
  Java 17/21/25 and H2/PostgreSQL are verified. Java 8/Boot 3 are not supported promises.
- New annotation members opt into strict validation, allowed fields and limits.
  Their defaults preserve the old matching policy. Applications manually
  implementing the annotation must implement its complete member contract.
- Query diagnostics now offer structured codes while retaining existing 400
  reasons for syntax, blacklist and conversion errors. HTTP rendering is consumer-owned.

This identity change introduces no new matching policy, grammar, framework
upgrade, module split or application migration. The inherited operator quirks
and association limitations are documented in the [query reference](reference/query-language.md).

## Boolean operator precedence correction

Pembana fixes an inherited operator-precedence inconsistency: `AND` now binds
more tightly than `OR`. Previously, both operators had equal precedence and
grouped left to right. This can change which records an existing search returns.

| Expression | Earlier grouping | Current grouping |
| --- | --- | --- |
| `A OR B AND C` | `(A OR B) AND C` | `A OR (B AND C)` |
| `A AND B OR C` | `(A AND B) OR C` | `(A AND B) OR C` |

Here, `A`, `B`, and `C` stand for complete conditions such as `name:Ada`.
For example, `name:Ada OR name:Beta AND active:true` now includes inactive Ada
records. To keep the earlier behavior, write:

```text
(name:Ada OR name:Beta) AND active:true
```

Review saved searches and application-generated expressions that mix `AND` and
`OR` without parentheses. Add parentheses around the groups you intend, then
check the results using your own data. Fully parenthesized expressions retain
their grouping; Java query objects also retain the structure you construct.

The correction applies to ordinary and collection expressions and to HTTP
binding, whether strict validation is enabled or disabled. There is no switch
for the earlier precedence. `BETWEEN` and `NOT BETWEEN` remain range conditions;
their internal `AND` is unchanged. The original grammar and test outcomes remain
recorded in the frozen historical reports.

For a worked example, see [combining conditions](reference/query-language.md#combining-conditions-with-and-or-and-parentheses).

## Case-insensitive string equality correction

`caseSensitiveFlag=false` now applies to string equality and inequality, as it
already did to patterns. For `Ada`, `name:ADA` now matches and `name!ADA` no longer
matches. This intentionally changes false-flag queries; the original true default,
membership, ordering, typed conversion and null-check operations are unchanged.
Use `true` when retaining exact comparison is required, subject to the database's
collation. First-class global/per-property request options now exist as a separate opt-in
policy; omitting them keeps the annotation behavior. JSON search binding requires
`@SearchRequestBody`, preserving existing POST body handling.
See [case sensitivity](reference/case-sensitivity.md) for current behavior and the
implemented binding and recommended UI design. Historical baseline reports remain frozen.

## JPA collection metadata and nested enum corrections

Collection emptiness now follows JPA metadata, so inherited and getter-mapped
collections behave like directly declared collection fields, including dotted
association paths. Nested enum values, including private nested types, are
converted using their resolved class rather than a canonical-name class lookup.
Valid queries that previously failed now execute; enum names remain case-sensitive,
invalid constants retain `INVALID_VALUE`, and unsupported collection operators
remain rejected. No matching defaults or public API signatures change.

## Historical evidence

Frozen Stage 0–4 reports retain old coordinates, class names, hashes, and upstream
references. Do not globally replace them. The original 113 method names and
assertions map to the new compatibility class by the table above; the identity
report records that mapping and equivalent outcomes. Upstream license/copyright
notices retain the original project name. Those references are provenance, not
current branding or installation advice.

## Consumer migration checks

1. Before publication, build/install the snapshot locally. After publication,
   configure the public snapshot repository described in [Getting started](getting-started.md#consume-a-published-snapshot).
2. Update imports/coordinates and any explicit resolver registration or exclusions.
3. Verify application queries and mandatory scope composition on its own schema.
4. Review strict validation choices separately; the rename does not turn it on.
5. Keep version/namespace adoption separate from future matching/model changes.

## Built-in Long conversion

Long and primitive long fields now use exact signed 64-bit decimal conversion by
default for equality, inequality, membership, ordering, and inclusive ranges.
Explicit null checks remain supported. Strict validation accepts these types.
Malformed and overflowing values produce `INVALID_VALUE` errors. Global text case
options do not affect numeric conversion. This bug fix intentionally replaces the
previous string fallback and strict unsupported-type rejection for Long fields;
no registration or feature switch is needed. Public signatures are unchanged.

## Opt-in plain-text search

New `SearchConfiguration`, `SearchMode`, `PlainTextQuery`, and `TextPattern` types
provide explicit plain-text composition. `SpecificationsBuilder.configured` sets
a consumer default; `withInput` selects or overrides it per query using `INHERIT`,
`STANDARD` (the original expression syntax and library default), or `SIMPLE`
(plain-text word and star-pattern matching). Existing
constructors, query records/enums, `withSearch`, `withQuery`, and HTTP binding
retain their contracts. See [plain-text mode](reference/plain-text.md) for
activation, deactivation, matching rules, and current root-String-only support.

## Opt-in field profiles

New immutable `SearchProfile`, `SearchField`, and `ProfileOperation` types provide
public names, alias resolution, operator permissions, SIMPLE fields and matching
defaults. Additive builder and translator overloads select profiles per query.
Existing calls apply no profile. Canonical access restrictions still apply after
resolution. See [field profiles](reference/profiles.md) for examples, precedence,
diagnostics and the current entity-path-only support boundary.

## Explicit converters and predicate extensions

New immutable `QueryExtensions` registrations provide field/exact-type conversion,
virtual/ordinary predicate factories and grouped criterion transformations. Select
them through distinctly named `withExtendedSearch` and `withExtendedQuery` methods;
existing constructors, overloads and default translation remain unchanged.
Profile source and transformed-target permissions apply before callbacks. See
[extension APIs](reference/extensions.md) for ordering, explicit declines, finite
expansion limits, matching choices, diagnostics and the standalone consumer example.

## Opt-in optional to-one handling

`QueryExtensions.Builder.optionalToOne(true)` selects left joins for traversed
ManyToOne/OneToOne paths in an individual extended query. Missing relations can
then satisfy other OR branches. Existing entry points, default/disabled registries,
validation and matching settings retain their behavior. `ToOnePaths.resolve`
provides the same explicit path resolution for custom factories. See
[optional to-one handling](reference/optional-to-one.md) for null behavior,
supported mapping shapes, per-query activation and limitations. Collection
traversal requires the separate collection policy described below. To-one navigation
after embedded attributes remains unsupported.

## Opt-in collection handling

Select `QueryExtensions.Builder.collections(true)` per query to search collection
contents with correlated existence or absence checks. Existing unconfigured queries
keep their behavior. Optional to-one paths before and inside a collection use left
joins; unrelated to-one paths still need `optionalToOne(true)`.

Separate conditions may match different children. `AssociationQuery.any` explicitly
requires one child to satisfy a group; parsed `BETWEEN` bounds also use one child.
The existing sealed `Query` variants and parser API remain unchanged. See
[collection handling](reference/collections.md) for examples, null and negative
semantics, supported mappings, extension callbacks, and current topology limits.
