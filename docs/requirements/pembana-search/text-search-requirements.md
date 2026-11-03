# Pembana Search Plain-Text Composition and Pattern Requirements

Status: Implemented; see [library verification](../../search-acceptance.md). Publication remains separate.
Date: 2026-11-03.
Source work: OPEN-01, OPEN-02; prior AC-19, AC-20.

## Objective and Activation

Provide a reusable plain-text composer and pattern utilities so consumers can
offer predictable text search across selected fields and storage mechanisms.
The [shared compatibility contract](compatibility-and-extension-requirements.md)
is mandatory. Existing advanced-expression and programmatic entry points shall
retain their current interpretation unless the caller selects a new API/policy.

The current immutable query tree and programmatic translator are already present.
Reuse them for existing operations. If a new pattern needs additional metadata,
use additive types/configuration without changing existing record components,
enum constants, or sealed variants. New literal string values shall not be encoded
into an advanced-expression string and parsed again.

## Plain-Text Composition

1. The public composer shall accept raw text, explicitly eligible fields, and
   matching configuration. It shall be callable without Spring, a database, a
   named profile, or consumer model classes. A field profile may provide these
   inputs when the consumer explicitly chooses one.
2. Under the new contains preset, input without stars shall be trimmed and split
   on whitespace. Each word shall match at least one eligible field, and every
   word shall match the same root record. Word order shall not matter. Build AND
   across words and OR across fields for each word, preserving grouping.
3. The contains preset shall use existing case-option mechanisms and default its
   own text matching to insensitive. It shall not change the annotation default
   or existing property/global/inherit precedence. Explicit sensitive choices
   shall remain available through the new configuration.
4. If the trimmed input contains `*`, the complete input shall be one explicit
   whole-value pattern, including internal whitespace. Each star shall match zero
   or more characters, including line breaks. Do not split the pattern into words
   or add implicit outer stars. A pattern shall match one eligible field value,
   never an artificial concatenation of fields.
5. Null, empty, or whitespace-only composer input shall produce an explicit neutral
   result with no text restriction. Nonblank input with no eligible fields shall
   produce a configuration error, not unrestricted search or inferred fields.
   Null field values shall not satisfy text comparisons. A wildcard-only pattern
   matches non-null values, including empty strings, rather than removing the
   filter for records whose eligible fields are all null.
6. Only `*` has wildcard meaning in this simple-text preset. Quotes, punctuation,
   backslashes, `%`, `_`, and regular-expression metacharacters are literal.
   Repeated stars are equivalent to one star. Do not introduce phrase quoting,
   Boolean keywords, or a backslash escape syntax into ordinary text implicitly.
7. Simple text and advanced expressions shall be separate explicit API choices.
   Simple text such as `AND` is literal. Advanced operators and field equality
   shall retain their selected expression policy; the composer shall not
   auto-detect or rewrite advanced expressions or hide parse errors.

## Shared Pattern Representation and Adapters

1. Provide an immutable, framework-independent representation of literal-contains
   and whole-value glob patterns. Offer equivalent in-memory evaluation and SQL
   LIKE parameter/escape-character generation, reusable by JPA and consumer JDBC
   queries. JDBC statement construction remains the consumer's responsibility.
2. Escape literal SQL `%`, `_`, and the chosen escape character before translating
   star tokens. Preserve case handling and anchoring; no intermediate string
   replacement shall confuse a literal character with an operator. Returned
   parameter text shall be bound data, not an SQL fragment or quoted SQL literal.
3. A consumer shall be able to translate the composed query through the JPA module
   using the new pattern semantics. Matching shall be applied before counting,
   limiting, or pagination rather than by filtering an already fetched page.
4. The in-memory adapter shall match the same declared inputs as SQL/JPA on the
   supported H2/PostgreSQL configurations. It shall not interpret user input as an
   unrestricted regular expression. Apply explicit configured input limits and
   avoid pathological wildcard/regex backtracking.
5. Preserve the existing case contract: Java normalization uses `Locale.ROOT`;
   database lower/collation behavior remains database-dependent. Document tested
   Unicode, null, and collation behavior without claiming universal case folding,
   accent removal, or equivalence on untested collations.
6. Existing advanced patterns retain literal internal stars and legacy SQL
   metacharacter behavior by default. A new literal/whole-glob policy shall be
   separately selected. An existing `Comparison(EQUALS, value)` containing a star
   shall not become a pattern merely because new pattern utilities are installed.

## Required Matching Examples

| Input in the new preset | Expected result |
| --- | --- |
| `aba`, `*aba*` | Match `Sabal` in insensitive mode. |
| `aba*` | Does not match `Sabal`; matches values starting with `aba`. |
| `*bal` | Matches `Sabal`; requires the end of the field value. |
| `sab*al` | Matches a value starting with `sab` and ending with `al`. |
| `minor sabal` | Matches a record containing both words in its eligible fields, in either order. |
| `sab* min*` | One value must start with `sab`, followed later by a literal space and `min`. |
| `100%`, `a_b`, `a.b+` | `%`, `_`, `.`, and `+` are literal characters. |
| `*`, `**` | Match a non-null eligible value, including an empty value; do not match an all-null record. |

The same examples shall also be exercised with sensitive case options and case
variants. Multiword tests shall include matches distributed across fields and a
record missing one word. Explicit patterns shall not bridge those fields.

## Acceptance Criteria

| ID | Evidence |
| --- | --- |
| TEXT-01 | COMPAT passes; unconfigured advanced and programmatic searches retain their exact baseline matching. |
| TEXT-02 | The composer implements all-word/any-field grouping for one and multiple fields without expression-string concatenation. |
| TEXT-03 | Full-input patterns preserve spaces, anchors, internal/repeated stars, and explicit case choices. |
| TEXT-04 | Neutral input, empty eligible fields, null values, empty strings, and wildcard-only input follow the specified distinct contracts. |
| TEXT-05 | Quotes, backslashes, SQL metacharacters, and regex punctuation remain literal under the new preset. |
| TEXT-06 | Core in-memory, JPA, and a generic consumer-JDBC sample agree on the declared corpus against H2 and PostgreSQL. |
| TEXT-07 | Predicates operate before pagination/counting; totals and sorted pages agree with the unpaged fixture. |
| TEXT-08 | Input-limit and repeated-star stress cases terminate predictably without unbounded regex backtracking. |
| TEXT-09 | Legacy/new consumers coexist; disabling policies on existing entry points restores baseline behavior. New composer APIs define their unconfigured outcome without auto-reinterpreting plain text as legacy expressions. |
| TEXT-10 | Documentation distinguishes new preset defaults, existing expression semantics, explicit activation, limits, and tested collation behavior. |
