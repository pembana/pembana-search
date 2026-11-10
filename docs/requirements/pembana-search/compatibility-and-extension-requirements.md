# Pembana Search™ Compatibility and Extension Requirements

Status: Required contract for all feature increments.
Date: 2026-11-02.
Adopted compatibility baseline: `e870eeb5f73e93ae5ec487787c879fd0d1e5232c`,
`1.0.0-SNAPSHOT`. The original comparison baseline was `52e9f94`.

## Accepted Migration Changes — 2026-11-02

The following changes from the original baseline are accepted migration work,
not release blockers and not changes that must be undone or placed behind a
legacy-behavior switch:

- `SearchSpec.profile()` was added. Consumers that implement the annotation
  interface by hand may add this member and recompile; binary compatibility with
  their earlier implementation is not required across this accepted transition.
- `AND` now binds more tightly than `OR`. Existing mixed expressions may need
  explicit parentheses to preserve a consumer's intended grouping.
- Strict validation is enabled by default, with 4,096-character and 16-level
  nesting limits. Consumers may use the documented overrides when appropriate.
- Inherited false-flag case handling now consistently applies to membership,
  ordering, and ranges as well as equality/patterns.
- Exact `Long`/`long` conversion and validation are built-in defaults; preserving
  the old strict rejection or non-strict fallback is not required.

Record these differences in migration notes and test their intended new behavior.
Update affected consumer source/tests and disclose changed outcomes rather than
claiming the original scenarios are unchanged. The remaining requirements below
preserve the adopted baseline for subsequent work; they do not promise unmodified
pre-transition clients will work. New profiles, custom extensions, search modes,
and association policies shall still be explicitly selected and pluggable. This
acceptance does not authorize unrelated future API/default changes.

## Objective and Compatibility Rule

New functionality shall be additive, explicitly selected, and pluggable. Updating
the dependency without changing application configuration shall preserve existing
source compatibility, binary compatibility, query results, error contracts, and
transport behavior. Selection of an improved policy may intentionally change its
documented behavior; callers that do not select it shall retain baseline behavior.

This contract is shared by [TEXT](text-search-requirements.md),
[FIELDS](field-profiles-and-converters-requirements.md), and
[ASSOC](association-query-requirements.md). New public type and method names shall
be documented in the implementation design; the requirements prescribe capability
and compatibility, not speculative signatures.

## Required Behavior

1. **Activation isolation.** New policies, converters, mappings, and factories shall
   require explicit selection or registration through a new public configuration
   API. Being present on the classpath shall not activate them. No component scan,
   discovered Spring bean, or global mutable registry shall silently change an
   existing consumer's behavior. Direct Java use shall work without Spring.
   No registry, an empty registry, and registrations that do not match a criterion
   shall preserve baseline behavior. An explicit handler decline shall delegate
   through the documented chain and existing fallback, subject to active field
   policy. Configuration/handler failures shall never become a neutral predicate.
2. **Independent choices.** Consumers shall be able to use plain-text composition,
   improved patterns, registered conversion, profiles, or association policies
   independently where their declared dependencies allow. Selecting one shall not
   silently enable strict validation or unrelated matching/join changes.
3. **Public API preservation.** Retain existing public methods, constructors,
   signatures, return contracts, record components, enum constants, and sealed
   query variants. Do not add required methods to existing interfaces or annotations.
   Adding annotation members with defaults is not sufficient compatibility for
   applications that implement the annotation interface by hand. Use new options
   types, registration APIs, or distinctly named methods without ambiguous existing
   overload resolution. Old consumers shall not need source edits or recompilation.
4. **Immutable model preservation.** Do not change the components, canonical
   constructors, equality/hash behavior, or representation of existing public
   records such as `SearchOptions`, `ParserOptions`, and query nodes. Do not expand
   existing enums or the sealed `Query` variants as a shortcut for new operations;
   exhaustive consumer code must keep compiling. Reuse existing nodes for supported
   operations; additional semantics may use separate opt-in types and adapters.
5. **Adopted matching.** Preserve default case sensitivity, explicit case-option
   precedence, consistent inherited case handling, outer-star interpretation,
   literal internal stars, and SQL `%`/`_` handling in existing entry points. Keep
   equality, nulls, empty membership, conversion, implicit joins, grammar precedence,
   quoting, and Boolean grouping as characterized unless a new policy is selected.
   In particular, preserve `AND` before `OR` and the adopted `INHERIT` behavior.
   Do not restore earlier inconsistencies in order to satisfy historical tests.
6. **Builder and error contracts.** Preserve replacement semantics of `withSearch`
   and `withQuery`, neutral `build()` behavior, existing validation settings and
   limits, exception types, error categories, reason strings, and metadata for
   existing scenarios. No silent fallback shall turn malformed advanced input
   into plain-text input. New APIs may define their own additive diagnostics.
7. **Transport compatibility.** Preserve GET/form parameter names, JSON opt-in,
   missing/empty/whitespace handling, duplicate/trailing JSON rejection, case-option
   inheritance, application mapper constraints, resolver replacement/backoff, and
   consumer-owned HTTP error rendering. Do not reinterpret existing requests as a
   new search mode. New HTTP binding, if supplied, shall have explicit activation
   and be unnecessary for direct Java APIs.
   Preserve fixed case-option names, JSON's fixed `search` member, mixed reserved
   URL/body rejection, unknown-member/type rejection, and no-arg resolver usage.
8. **Module boundaries.** Core shall remain independent of Spring, persistence,
   and application entities. JPA integration shall work without the starter.
   Preserve coordinates, Java 17 bytecode, and the declared framework baseline.
   Do not bundle these increments with framework/database upgrades or reintroduce
   Kotlin tooling/runtime dependencies.
9. **Extension lifecycle.** Configuration shall be immutable or defensively copied,
   scoped to the configured consumer, and usable by concurrent requests. Cache
   metadata only where safe; never retain JPA roots, joins, queries, or mutable
   per-request state between executions. Ambiguous/duplicate registrations shall
   fail deterministically before query execution.
10. **Required restrictions.** Field/operator restrictions shall apply before
    invoking a registered factory. Preserve caller-owned mandatory predicates as
    outer constraints on the complete user query. Extension code is trusted
    application code, not a sandbox; its responsibilities shall be documented.
    Parameter values shall remain data rather than executable query structure.

## Verification and Release Requirements

1. Pin and record the exact adopted baseline and candidate revisions. Preserve the full
   existing corpus, fixtures, assertions, scenario identities, and effective test
   execution. Add characterization coverage separately where a gap is identified.
   The explicitly accepted transition may update affected expectations with
   documented reasons; do not treat it as unchanged historical behavior.
2. Verify both source and binary compatibility. Compile representative consumers
   against the old API and run their unchanged compiled classes against the new
   artifacts. Also compile unchanged source against the new artifacts. Include
   handwritten annotation implementations, record constructors, exhaustive enum
   handling, and existing builder/translator constructors.
3. Exercise each feature unconfigured, explicitly disabled, explicitly enabled,
   and alongside another independently configured consumer. Demonstrate rollback
   to baseline behavior through deactivation alone for policies decorating existing
   entry points. A new API with no baseline equivalent shall instead define its
   disabled/unconfigured outcome and leave legacy entry points unchanged; a direct
   call to an explicitly opt-in API may itself be its activation. Do not reinterpret
   new input as a legacy expression or silently remove a filter on deactivation.
   Selecting an application fallback remains the consumer's responsibility.
4. Run the declared Java 17, 21, and 25 consumer/test-runtime matrix against H2 and
   PostgreSQL. Build tools may use the existing Java 21 requirement; emitted
   bytecode shall remain Java 17. Report actual outcomes rather than assuming a
   passing dependency build establishes consumer compatibility.
5. Meet existing nullability, formatting, JavaDoc, packaging, and coverage gates.
   Changed production lines shall have at least 80% line coverage, meaningful
   branch/failure cases, and documented measurement gaps. Do not weaken existing
   gates, assertions, or tests to achieve compatibility.
6. Retain Apache 2.0 and inherited MIT notices and provenance in binaries, source,
   and JavaDoc artifacts. Document the new public API, dependencies, activation,
   deactivation, examples, limitations, and preserved legacy behavior. Keep
   independent versioning and release notes; do not claim publication merely
   because local staging or a verification build succeeds.
   Keep generic examples and a standalone sample consumer. Library branding shall
   not introduce an application dependency or couple its release cycle to one
   product's deployments.

## Acceptance Criteria

| ID | Evidence |
| --- | --- |
| COMPAT-01 | Unchanged baseline scenarios pass with equivalent results/errors and no new skips when new features are not selected. |
| COMPAT-02 | Old source consumers compile and old precompiled clients run against the candidate, including handwritten annotations and existing records/enums. |
| COMPAT-03 | Existing-entry-point policies restore baseline behavior when disabled; new APIs define unconfigured outcomes. No/empty/unmatched/declining registrations retain documented fallback, failures are not swallowed, and consumers remain isolated. |
| COMPAT-04 | Existing GET/form/JSON requests, resolver replacement, mapper constraints, and error rendering retain their contracts. |
| COMPAT-05 | Existing builder replacement and programmatic/parsed-query equivalence tests pass. |
| COMPAT-06 | Module independence, coordinates, Java 17 bytecode, no Kotlin requirement, license payloads, and the six database/runtime combinations are verified. |
| COMPAT-07 | Concurrent consumers and repeated queries demonstrate no leaked policy or JPA query state. |
| COMPAT-08 | New public documentation, changed-code coverage, compatibility reports, deactivation instructions, and release checklist accompany the increment. |

## Baseline Evidence

See the adopted [configuration reference](https://github.com/pembana/pembana-search/blob/e870eeb5f73e93ae5ec487787c879fd0d1e5232c/docs/reference/configuration.md),
[case-option reference](https://github.com/pembana/pembana-search/blob/e870eeb5f73e93ae5ec487787c879fd0d1e5232c/docs/reference/case-sensitivity.md),
[query reference](https://github.com/pembana/pembana-search/blob/e870eeb5f73e93ae5ec487787c879fd0d1e5232c/docs/reference/query-language.md),
and [migration guide](https://github.com/pembana/pembana-search/blob/e870eeb5f73e93ae5ec487787c879fd0d1e5232c/docs/migration.md).
These distinguish the accepted transition from behavior to preserve subsequently.
