# Pembana Search Association Query Policy Requirements

Status: Implemented; see [library verification](../../search-acceptance.md). Publication remains separate.
Date: 2026-11-03.
Source work: OPEN-04; prior AC-22.

## Objective and Activation

Offer a configurable JPA association policy that preserves matches across optional
relations and expresses collection existence/absence deliberately, with correct
root counts and pages. Apply the
[shared compatibility contract](compatibility-and-extension-requirements.md) and
the public extension architecture in
[FIELDS](field-profiles-and-converters-requirements.md).

The baseline documents an implicit-inner-join limitation: a record with no author
is omitted from `author.name:Writer OR title:Orphan`, even if its title matches.
This behavior shall remain in existing unconfigured entry points. The new policy
shall be explicitly selected for a translator/profile; no global join rewrite
shall activate merely because the new code is installed.

## Optional To-One and Collection Predicates

1. Under the selected improved policy, a record with a missing optional association
   shall remain eligible for another matching OR branch. Positive comparisons on
   a missing associated scalar shall not invent a matching value. Maintain SQL
   null behavior for ordinary scalar comparisons; use explicit null predicates
   where null matching is requested.
2. Provide supported query-local resolution through appropriate left joins or
   correlated predicates. Define the supported relation topologies and mappings;
   do not claim universal association support based on one fixture. Retain existing
   getter/inherited-property and dotted-path collection-emptiness support.
3. A positive collection field predicate shall mean that a matching child exists.
   It shall not multiply root rows when multiple children match. Separate positive
   leaves joined with AND shall default to independent child existence.
4. Provide an explicit programmatic association scope for consumers that require
   several criteria to match the same child. Preserve that scope when combining
   or negating queries; do not infer it by flattening AND expressions or sharing
   whichever join happened to be created first. New DSL syntax is not required.
5. In the new collection absence policy, negated equality/pattern/membership shall
   mean that no child satisfies the corresponding positive predicate, rather than
   that some child satisfies the opposite scalar predicate. An empty collection
   therefore satisfies absence and does not satisfy positive existence. Where a
   consumer needs explicit existence of a nonmatching child, expose that through
   the explicit association scope rather than conflating the two meanings.
6. Null child values retain SQL predicate truth rules inside their declared scope.
   A child-field `IS NULL` test shall require an actual child with a null field;
   it shall not imply an empty association. Parent collection emptiness shall use
   the existing explicit emptiness operation. Document and test negation of these
   cases, and preserve baseline semantics when the policy is disabled.
7. Preserve the query tree's AND/OR/grouping/negation and caller-owned mandatory
   predicates. Consumer field restrictions, converter selection, and case choices
   shall apply inside association predicates through the same public mechanisms.
8. Root queries and their count queries shall identify the same matching roots.
   Avoid post-pagination deduplication and filtering. Do not impose library-owned
   sorting, override caller sorting, add unsafe fetch joins to count queries, or
   treat `distinct` as sufficient evidence that every topology is correct.
9. Reuse joins/metadata only within the correct query and declared association
   scope. Do not cache Criteria roots, joins, subqueries, or mutable scope state
   across page/count executions or consumer threads. Bind user values and retain
   consumer/database index ownership.

## Required Generic Fixtures

- Books with and without authors, including an orphan whose title satisfies an
  OR branch and another that does not. The selected policy returns the matching
  orphan; the unconfigured baseline continues its characterized behavior.
- Parent records with no labels, `{red}`, `{blue}`, and `{red, blue}`. Positive
  existence of red matches the red-bearing records. Absence of red matches the
  empty and blue-only records. Explicit existence of a non-red child matches
  blue-only and mixed records, demonstrating the distinct quantifiers.
- Children `(red, small)` and `(blue, large)` on one parent, and `(red, large)` on
  another. Independent existence satisfies both parents for red AND large;
  explicit same-child scope satisfies only the latter.
- Null child fields, repeated matching children, inherited/getter-mapped
  collections, nested optional relations within the declared support boundary,
  and mandatory consumer predicates around mixed AND/OR/NOT queries.
- An empty direct collection combined with a matching different collection or
  scalar OR branch; the empty relation shall not suppress that other match.

## Acceptance Criteria

| ID | Evidence |
| --- | --- |
| ASSOC-01 | COMPAT passes; default implicit-join, null, collection-emptiness, and pagination behavior is preserved. |
| ASSOC-02 | The opt-in optional-to-one policy returns a root satisfying another OR branch even when the association is absent. |
| ASSOC-03 | Positive existence returns roots once despite multiple matching children. |
| ASSOC-04 | Absence differs from existence of a nonmatching child on empty and mixed collections, including pattern and membership negation. |
| ASSOC-05 | Independent-child AND and explicit same-child scope produce the fixture's different expected results and compose under negation. |
| ASSOC-06 | Null child fields and empty associations remain distinguishable through explicit null/emptiness operations. |
| ASSOC-07 | Parsed and programmatic mixed Boolean queries retain mandatory scopes, permissions, converters, and case choices; an empty relation does not suppress another matching OR branch. |
| ASSOC-08 | Unpaged results, totals, and multiple sorted pages agree on H2/PostgreSQL with no post-page deduplication. |
| ASSOC-09 | Repeated page/count executions and concurrent consumers show no leaked Criteria state or policy settings. |
| ASSOC-10 | Documentation states supported topologies, null/negative/same-child semantics, activation, deactivation, limitations, and extension examples. |
