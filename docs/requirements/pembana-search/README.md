# Pembana Search™ Library Feature Handoff

Status: Library implementation verified locally. See [acceptance evidence](../../search-acceptance.md).
Date: 2026-11-03.

This directory is a self-contained handoff for the **Pembana Search™ library**.
These requirements are now maintained in the library repository. Their normative
references are self-contained; no application repository or entity model is needed
to implement or verify the library contracts.

The adopted compatibility baseline is
[`e870eeb5f73e93ae5ec487787c879fd0d1e5232c`](https://github.com/pembana/pembana-search/tree/e870eeb5f73e93ae5ec487787c879fd0d1e5232c),
version `1.0.0-SNAPSHOT`. The reviewed source already has Java production/tests,
three modules, immutable queries, programmatic construction, JPA translation,
validation limits, structured errors, and global/per-property case options. Those
are retained foundations, not features to rebuild. Earlier reports describe their
own pinned revisions. Current verification and delivery boundaries are recorded
in the [acceptance report](../../search-acceptance.md).

The [accepted migration changes](compatibility-and-extension-requirements.md#accepted-migration-changes--2026-11-02)
permit the added annotation member, precedence/default-validation changes,
consistent inherited case handling, and built-in Long conversion. They are
consumer migration work rather than library blockers. Future unrelated changes
still follow the compatibility and explicit-activation contract. A later source
increment, `88c893c`, adds nested/collection SIMPLE fields. Its local library
verification is recorded here; application adoption and published artifact parity
are separate delivery concerns.

## Requirements Packages

| Package | Source work | Requirements document | Delivery |
| --- | --- | --- | --- |
| COMPAT | OPEN-08 and compatibility requirements for every package | [Compatibility and extension architecture](compatibility-and-extension-requirements.md) | Verified locally against the adopted baseline. |
| TEXT | OPEN-01, OPEN-02; prior AC-19, AC-20 | [Plain-text composition and patterns](text-search-requirements.md) | Implemented, including nested-field support and JDBC parity verification. |
| FIELDS | OPEN-03, OPEN-05, OPEN-06; prior AC-21, AC-23, AC-24 | [Field profiles, converters, and predicate extensions](field-profiles-and-converters-requirements.md) | Implemented; regression suites exercise activation, permissions, and isolation. |
| ASSOC | OPEN-04; prior AC-22 | [Association query policies](association-query-requirements.md) | Implemented and verified on the supported generic association fixtures. |

Every package shall satisfy COMPAT. TEXT's plain-word composition may ship before
the rest of FIELDS; it shall accept explicitly selected fields using the existing
access controls. Sharing a configuration mechanism does not require making all
features depend on one monolithic profile. ASSOC shall use supported public
extension points rather than consumer access to parser internals. Separate
feature increments and defaults-disabled tests are required.

## Ownership and Exclusions

Only generic library mechanisms are included. The following remain consumer work:
screen/controller changes, searchable-field selection, public/admin policies,
tenant and publication restrictions, domain-specific tag inheritance, historical
names, external identifier interpretation, lookup fallback policy, labels, help,
exports, saved-view behavior, native SQL structure, and index optimization.

The library shall provide a generic sample consumer demonstrating composition,
extensions, and required-scope composition. It shall not modify a botanical
application, hard-code its entity paths, or require that application as a test
dependency. A display value such as a catalog identifier is a generic extension
example, not a request for a built-in domain identifier format.

Fuzzy/semantic search, accent removal, a visual query builder, a full-text index,
and a general JDBC/SQL query compiler are excluded. SQL LIKE parameter generation
in TEXT is a small reusable pattern adapter, not a complete persistence backend.

## Definition of Done

1. Each delivered package meets its own acceptance criteria and COMPAT, with
   activation, deactivation, and coexistence evidence.
2. Consumers migrated for the accepted transition compile and run. Subsequent
   source/binary compatibility checks use the adopted baseline. Tests for accepted
   behavior changes document their revised expectations; unrelated baseline
   scenarios retain their assertions and effective execution. New opt-in behavior
   has separate tests.
3. The documented Java 17/21/25 runtime by H2/PostgreSQL matrix passes, together
   with packaging, nullability, formatting, JavaDoc, and changed-code coverage gates.
4. Release notes identify new APIs, opt-in settings, preserved legacy behavior,
   test evidence, and known limits. Publication remains a separate explicit action.

The original application's AC-25 and OPEN-07 are intentionally excluded from the
library's delivery gates. They are consumer adoption tasks, not proof that the
library implementation is complete.
