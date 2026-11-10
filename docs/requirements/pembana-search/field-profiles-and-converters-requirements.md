# Pembana Search™ Field Profiles, Converters, and Predicate Extensions

Status: Implemented; see [library verification](../../search-acceptance.md). Publication remains separate.
Date: 2026-11-02.
Source work: OPEN-03, OPEN-05, OPEN-06; prior AC-21, AC-23, AC-24.

## Objective and Activation

Provide public extension APIs for searchable fields, aliases, operators, value
conversion, and custom predicate generation. Apply the
[shared compatibility contract](compatibility-and-extension-requirements.md).
Existing allowlists, validation, immutable queries, and programmatic translation
already exist and shall be reused rather than replaced.

New profiles and registrations shall be independently supplied through additive
configuration. No global registry or new `@SearchSpec` member is required. Legacy
constructors, annotation implementations, records, default validation behavior,
and unregistered type handling shall remain unchanged.

## Field Profiles

1. An immutable named profile shall define accepted fields, optional public
   aliases, allowed operators, optional default text-search fields, and matching
   defaults. A field may resolve to an entity path or a registered virtual-field
   predicate factory. Aliases shall use the existing expression identifier
   alphabet; expanding the legacy grammar is outside this increment.
2. A selected profile shall resolve names deterministically. Ambiguous aliases,
   duplicate names, cycles, unresolved mappings, and incompatible configuration
   shall fail during profile construction. Aliases shall not require rewriting
   expression strings with regular expressions.
3. Profile-controlled queries shall accept only explicitly registered public names
   or canonical paths that the profile exposes. Apply the existing canonical path
   allow/deny restrictions after alias resolution; blacklist precedence remains.
   Resolved virtual fields shall have explicit access and operator rules.
4. Normalize case-option keys through the same selected mapping. Existing
   property/global/annotation precedence shall remain. Conflicting options for two
   names resolving to the same field shall be rejected rather than chosen by map
   iteration order. Inactive profiles retain the old exact-path option behavior.
   Per-field case choices shall name fields present in the query and require String
   attributes; global case choices continue to ignore non-String attributes.
5. The same profile shall apply to parsed expressions and programmatic queries,
   including membership, negation, grouping, and range expansion. A disallowed
   alias/operation shall fail before a custom predicate is invoked.

## Converters and Exact 64-Bit Values

1. Provide public registration by exact Java type and by registered field, with
   field-specific conversion taking precedence over type registration, then
   built-in behavior. Duplicate registrations at the same precedence shall be
   configuration errors. Conversion, strict validation, and permitted operators
   shall agree on supported types before constructing persistence predicates.
2. Supply an exact `Long`/primitive `long` converter and operator contract covering
   equality, inequality, membership, ordering, inclusive ranges, and explicit null
   checks. Parse decimal input without floating-point or 32-bit intermediates.
   Support both signed 64-bit boundaries; reject malformed and overflow values
   with structured diagnostics rather than provider-dependent coercion.
3. Built-in exact Long conversion/validation is an accepted default in the adopted
   baseline. Restoring the earlier strict rejection/non-strict fallback or adding
   an opt-in solely for Long support is not required. Custom converter overrides
   shall still require explicit registration and remain isolated from unrelated
   fields and types.
4. Numeric case options shall not change numeric meaning. Scalar, list, and range
   conversion shall use the same exact converter; avoid a separate lossy path for
   parsed expressions, programmatic values, or JSON/query binding.
5. A consumer shall be able to supply a converter for a custom external code or
   identifier format. Do not embed botanical identifiers, base32 TSID rules, or
   application classes in the library. Registered conversion shall not imply that
   all operators are meaningful for that custom type.

## Predicate and Transformation Extension API

1. Support registered criterion transformations and predicate factories for ordinary
   and virtual fields. A factory shall receive the resolved field, operation,
   validated/converted value, effective matching policy, and query-local JPA
   context needed for its declared capability.
2. Factories shall support both parsed and programmatic queries through the same
   translation path. No generated parser types, internal visitors, reflection into
   private state, or copied strategies shall be required in consumer code.
3. Transformations may produce grouped combinations of existing query operations.
   They shall preserve outer Boolean grouping and required consumer scopes. Detect
   cycles or recursively repeated expansion; validation and finite configured work
   limits shall apply to transformed trees as well as submitted input.
4. A transformed target shall remain subject to the selected profile's target
   permissions. An alias or virtual-field expansion shall not implicitly authorize
   arbitrary new paths. Trusted application factories remain responsible for their
   own SQL/JPA construction; registration is not an isolation sandbox.
5. Configuration shall be immutable and query execution state local. Define the
   extension ordering and failure behavior publicly. Factory/converter failures
   caused by invalid input shall have actionable diagnostics; do not misclassify
   infrastructure failures or faulty application extensions as invalid user input.

## Acceptance Criteria

| ID | Evidence |
| --- | --- |
| FIELDS-01 | COMPAT passes against the adopted baseline, including migrated handwritten annotation implementations and the accepted built-in Long behavior. |
| FIELDS-02 | Profiles resolve aliases, fields, operators, and case options identically for parsed and programmatic queries. |
| FIELDS-03 | Duplicate/cyclic mappings, conflicting options, unsupported operators, and inaccessible transformed targets fail deterministically. |
| FIELDS-04 | Registered Long queries preserve `-9223372036854775808`, `9223372036854775807`, and `9007199254740993` exactly in scalar/list/range cases on H2 and PostgreSQL. |
| FIELDS-05 | Values just outside the Long range, malformed values, and invalid list/range elements yield structured errors without precision loss. |
| FIELDS-06 | Built-in Long conversion follows the adopted baseline; custom conversions are opt-in and do not affect unrelated fields/consumers. |
| FIELDS-07 | Field/type/built-in precedence and custom external-code conversion work without a domain dependency or global registration side effect. |
| FIELDS-08 | A generic sample registers a virtual field, a grouped criterion transformation, and a predicate factory without parser-internal imports. |
| FIELDS-09 | Transformation cycles/size limits, access checks, required scopes, concurrent configurations, and query-state isolation have tests. |
| FIELDS-10 | Documentation covers public extension lifecycle, precedence, diagnostics, activation/deactivation, trusted-code responsibilities, and supported operators. |
