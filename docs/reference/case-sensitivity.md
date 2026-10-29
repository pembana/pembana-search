# Case sensitivity

## Current API and recommended form default

For ordinary names and descriptive text, a case-insensitive form default usually
matches user expectations. Exact codes or identifiers may need a different
policy. Pembana Search's existing annotation default remains
`caseSensitiveFlag=true` so upgrading does not change every consumer's queries.
Configure an endpoint explicitly to give its form a case-insensitive default:

```java
@SearchSpec(caseSensitiveFlag = false)
Specification<User> search
```

Use `caseSensitiveFlag=true`, or omit the member, to retain case. This is an
endpoint/translation policy, not a request parameter. Adding
`?caseSensitiveFlag=false` to a URL does not override the annotation automatically.
The same policy is supplied to `CriteriaParser`, `SpecificationsBuilder` and
`JpaQueryTranslator`; parsed expressions and programmatic trees use the same
JPA strategy. Strict validation does not change the chosen case policy.

## Equality and inequality

On the tested default H2/PostgreSQL configurations, for a stored value `Ada`:

| Expression | `caseSensitiveFlag=true` | `caseSensitiveFlag=false` |
| --- | --- | --- |
| `name:Ada` | Matches | Matches |
| `name:ADA` | Does not match | Matches |
| `name:ada` | Does not match | Matches |
| `name!ADA` | Matches | Does not match |
| `name:*ADA*` | Does not match | Matches |

With `false`, equality and inequality compare the SQL-lowercased field with the
input lowercased using Java `Locale.ROOT`. The value remains a bound Criteria
value; the implementation does not interpolate input into SQL or expression
syntax. With `true`, the original field/value are compared. Neither setting
adds outer wildcards to equality. Use explicit wildcard operations when you want
prefix/suffix/contains matching.

Programmatic construction follows the same rule:

```java
Query query = new Comparison("name", ComparisonOperator.EQUALS, "ADA");
Specification<User> predicate = new JpaQueryTranslator<User>(settings).translate(query);
```

Here `settings` is the application's `SearchSpec` instance. Choose its case flag
as shown in the [direct JPA example](../getting-started.md#direct-jpa-usage-without-mvc).
A core `Comparison` currently has no case override: case is an adapter policy,
not something embedded in the value string or selected by capitalization.

## Current operator boundaries

| String operation | Behavior when the flag is false |
| --- | --- |
| Equality `:` and inequality `!` | Fold field and value |
| Outer-wildcard patterns and negated patterns | Fold field and pattern, as before |
| `IN` / `NOT IN` | Retain original field/items; membership folding is separate work |
| `>` / `>:` | Retain the existing folded-field/folded-value behavior |
| `<` / `<:` | Retain the existing original-field/folded-value asymmetry |
| `IS NULL` / `IS NOT NULL` | Retain SQL null checks |

Numeric, Boolean, temporal, UUID and enum conversion strategies are unchanged.
Enum names remain case-sensitive. No first-class per-field override, automatic
HTTP toggle, new expression operator or grammar syntax is introduced by the
equality fix. Do not assume the current flag makes every string operation
consistent; the remaining differences are listed explicitly above.

## Per-property control with the current programmatic API

An application can already translate individual field queries with different
`SearchSpec` policies and compose the resulting specifications:

```java
Specification<User> foldedName = new JpaQueryTranslator<User>(insensitiveSettings)
        .translate(new Comparison("name", ComparisonOperator.EQUALS, nameInput));
Specification<User> exactCode = new JpaQueryTranslator<User>(sensitiveSettings)
        .translate(new Comparison("code", ComparisonOperator.EQUALS, codeInput));
Specification<User> effective = tenantScope.and(foldedName.and(exactCode));
```

`insensitiveSettings` has `caseSensitiveFlag=false`; `sensitiveSettings` has
`true`. Both are application-owned `SearchSpec` instances, and `tenantScope`
is its mandatory authorization restriction. The property names must be permitted
by the policies. User values stay as values rather than expression fragments.

This provides mixed field behavior for a form that builds predicates
programmatically. The application must validate and resolve the form's choices
and preserve its intended AND/OR grouping. It does not add automatic overrides
to a single parsed expression or a built-in per-property HTTP options map. A
first-class override API would remove that application-side assembly work.

## Nulls, literals, locale and database effects

A null database value is not the string `"null"`. String inequality still follows
SQL null semantics and does not include null rows merely because they differ
from the input. Use `IS NULL`, or combine it with inequality using OR, when that
is the intended query. Equality to quoted `'NULL'` remains a string comparison;
with folding, it can match a literal `NuLl` value. Low-level Java null comparison
values remain null instead of being converted to a text literal.

Java's `Locale.ROOT` normalizes the bound input independently of the process's
locale. SQL `lower()` is still database-controlled: H2 can follow the JVM locale,
and PostgreSQL behavior depends on its database configuration/collation. This
is not universal Unicode case folding, accent removal, transliteration or fuzzy
matching. A true flag skips explicit folding; it does not force a binary or
case-sensitive comparison on a database configured with an insensitive collation.
Test production collations and any non-ASCII domain-specific cases explicitly.

Applying `lower(column)` can affect index use. Assess query plans on the real
schema; an appropriate expression index or database-specific type/collation may
be needed. Indexes and collation choices remain application responsibilities.
The fix does not alter SQL LIKE `%`/`_` handling, internal `*` interpretation,
association joins or mandatory-scope composition.

## Suggested form UX and follow-up override design

The following is a recommended future request/policy design, **not an implemented
Pembana Search API**:

1. Default ordinary form text to case-insensitive matching.
2. Give identifiers/codes configured field defaults where exact case matters.
3. Offer an advanced global case toggle for the current search.
4. Allow a text property to choose **Use default**, **Case-insensitive**, or
   **Case-sensitive**, and show its effective value. Numeric/date properties do
   not need a case control; enum conversion currently remains exact.

Resolve the setting in this order:

| Priority | Choice |
| --- | --- |
| 1 | Explicit per-property choice in this request |
| 2 | Explicit global choice in this request |
| 3 | Server-configured default for that property |
| 4 | Form/application default, normally case-insensitive |

An omitted global choice must remain distinct from an explicit `false`. “Use
default” clears the override rather than forcing a value. If some identifier must
always use an exact policy, make that a server-enforced rule instead of a
user-overridable default.

Pass choices as structured options alongside a query, rather than adding magic
characters to field names or values. The server should validate option keys,
retain its searchable-field/operator restrictions and compose authorization
scopes outside the entire user expression. The UI toggle is a matching choice,
not an authorization mechanism. Parsed and programmatic queries should resolve
options identically. A follow-up implementation should specify consistent rules
for equality, patterns and membership before adding the override API.

## Upgrade note and verification

Earlier snapshots ignored `caseSensitiveFlag=false` for string equality and
inequality. They now honor it. This is an intentional correction: the same query
may return more equality matches and fewer inequality matches. Applications
requiring the previous exact comparison should use `true` and verify their
production collation. The annotation default itself has not changed.

The original 113 upstream test methods remain unchanged. The Stage 4 test that
asserted case-sensitive equality under a false flag is intentionally updated to
assert the corrected result; its membership assertion remains unchanged. Eight
new regressions cover case variants, both flag values, strict mode, null/literal
handling, programmatic parity, typed fields and ROOT normalization of bound
values. See the [verification report](../case-sensitivity/README.md).
