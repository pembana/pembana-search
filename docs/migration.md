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

## Strict validation and input limits by default

A basic `@SearchSpec` now enables strict validation, permits up to 4,096 input
characters, and permits up to 16 nested levels of parentheses. Core-only parsing
with `new ExpressionParser()` or `ParserOptions.defaults()` uses the same defaults.
Earlier snapshots used permissive validation and unlimited length and nesting.

This changes how invalid and oversized requests behave. For example,
`active:banana` now reports `INVALID_VALUE` instead of searching for `false`.
Unknown fields and unsupported operations produce structured query errors.
Expressions exceeding a limit report `QUERY_LIMIT_EXCEEDED`. Applications retain
control of how HTTP error bodies are rendered.

Start by checking saved searches and tests against these defaults. Correct invalid
values and choose larger limits if valid searches need them. For example:

```java
@SearchSpec(allowedFields = {"name", "active"},
            maxQueryLength = 8192, maxNestingDepth = 32)
Specification<User> search
```

To explicitly retain the earlier permissive, unlimited input policy:

```java
@SearchSpec(strictValidation = false, maxQueryLength = 0, maxNestingDepth = 0)
Specification<User> search
```

For core-only compatibility, pass
`new ParserOptions(false, List.of(), List.of(), 0, 0)` to `ExpressionParser`.
Disabling strict validation and disabling each limit are independent choices.
Neither changes the corrected `AND`-before-`OR` grouping.

Searchable fields remain application-defined. An empty allowlist imposes no field
restriction; use an allowlist or field profile for public endpoints. Input limits
bound parsing work, not SQL execution time. Keep tenant and authorization
restrictions outside the complete user-controlled expression.

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
typed conversion and null-check operations are unchanged. Membership and ordering
were corrected subsequently as described [below](#consistent-case-sensitivity-for-membership-and-ordering).
Use `true` when retaining exact comparison is required, subject to the database's
collation. First-class global/per-property request options now exist as a separate opt-in
policy; omitting them keeps the annotation behavior. JSON search binding requires
`@SearchRequestBody`, preserving existing POST body handling.
See [case sensitivity](reference/case-sensitivity.md) for current behavior and the
implemented binding and recommended UI design. Historical baseline reports remain frozen.

## Consistent case sensitivity for membership and ordering

`@SearchSpec(caseSensitiveFlag=false)` now ignores case consistently for all
String comparisons. This corrects inherited behavior in earlier snapshots:

| Search on a stored name `Ada` | Earlier false-flag behavior | Current false-flag behavior |
| --- | --- | --- |
| `name IN [ADA]` | Does not match | Matches |
| `name NOT IN [ADA]` | Matches | Does not match |
| `name<ada` | Could match because only the input was folded | Does not match: both sides are `ada` |
| `name BETWEEN A AND A` | Could match because the upper bound used the original field | Does not match |

Review searches that inherit a false annotation flag and use membership,
less-than comparisons, or ranges. The change applies with either validation
setting, to parsed expressions and Java query objects, and to supported
collection elements. Earlier explicit `insensitive` options already folded
consistently; those results are unchanged.

The true annotation default still skips folding. To request case-sensitive
matching for one String property while other properties ignore case, use
`fieldCaseSensitivity[name]=sensitive` in GET requests, or the corresponding
`fieldCaseSensitivity` JSON property. A global `caseSensitivity=sensitive` applies
to all String properties. These choices override the annotation flag. They do
not reproduce the earlier inconsistent less-than behavior.

SQL null handling, empty membership, non-String conversions and pattern syntax
are unchanged. Database collation still affects comparison behavior. See the
[case guide](reference/case-sensitivity.md) for examples and request formats.

## JPA collection metadata and nested enum corrections

Collection emptiness now follows JPA metadata, so inherited and getter-mapped
collections behave like directly declared collection fields, including dotted
association paths. Nested enum values, including private nested types, are
converted using their resolved class rather than a canonical-name class lookup.
Valid queries that previously failed now execute; enum names remain case-sensitive,
invalid constants retain `INVALID_VALUE`, and unsupported collection operators
remain rejected. No matching defaults or public API signatures change.

## HTTP field-profile selection

`@SearchSpec` now has `profile`, defaulting to an empty string. Existing annotation
usages retain ordinary canonical field handling. Registering profile beans alone
does not apply them to endpoints. Select a registered `SearchProfile.name()` in
the annotation to enable its public fields, aliases and permissions for GET/form
or JSON binding. See the [HTTP profile walkthrough](reference/profiles.md#use-a-profile-in-an-http-endpoint).

Applications manually implementing the `SearchSpec` annotation interface must
implement `profile()` and include it in annotation equality/hash calculations.
Return `""` to retain ordinary behavior. Custom resolvers can pass their registered
profiles to the new `(JsonMapper, Collection<SearchProfile>)` constructor.

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
