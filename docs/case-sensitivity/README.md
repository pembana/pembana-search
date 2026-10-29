# Case-insensitive equality verification

Baseline: signed revision `19353b9`. String equality and inequality now honor
`caseSensitiveFlag=false`, using the already normalized field/input. The true
default is unchanged. This is an intentional correction of a characterized
upstream asymmetry, not a grammar change or a new matching-policy API.

See the [case-sensitivity guide](../reference/case-sensitivity.md) for examples,
upgrade instructions, database limitations and the recommended form/default/
per-property UX. Automatic HTTP toggles and first-class per-field controls remain
proposed follow-up work and are not advertised as implemented configuration.

## Regression evidence

`CaseInsensitiveEqualityTest` adds eight methods covering:

- Equality and inequality across mixed case variants, including strict validation.
- The unchanged true default and exact comparison behavior on tested collations.
- Nullable database values, inequality's SQL null semantics and literal `NULL` text.
- Low-level Java null values staying null rather than becoming the string `null`.
- Programmatic/expression parity and unaffected numeric/Boolean fields.
- Java ROOT normalization of bound equality/inequality values under a Turkish
  process locale, tested independently of SQL folding.

Running those eight methods against the baseline produces **four assertion
failures, zero errors and zero skips**: false-flag equality, false-flag inequality,
folded bound-value use and nullable-field mixed-case equality. These are expected
regression demonstrations; the fixed implementation passes all eight. Coverage
was disabled only for that isolated, intentionally partial baseline selection.

The original 113 upstream methods are unchanged. The prior Stage 4 method
`insensitivePatternsFoldCaseButEqualityDoesNot` is intentionally renamed to
`insensitiveEqualityAndPatternsFoldCaseButMembershipDoesNot`. Its uppercase
false-flag equality now expects `Ada`; its membership expectation is unchanged.
The old baseline reports/inventories are preserved as historical records rather
than rewritten to make this behavior change look compatible.

## Verification matrix

Each combination discovers and executes **193 tests**: core 26, JPA 159, starter 8.
All six combinations have **zero failures, errors or skips**:

| Test runtime | H2 | Testcontainers PostgreSQL 18.1 |
| --- | --- | --- |
| Java 17 | Pass | Pass |
| Java 21 | Pass | Pass |
| Java 25 | Pass | Pass |

Maven/Checkstyle/compilation use Temurin 21.0.10 and target Java 17. Every module
passes production/test nullability checks, Checkstyle, strict Javadoc and its
unchanged 80% instruction / 75% branch coverage gates. All four changed executable
production lines are covered. Grammar, dependency declarations, typed converters
and existing database fixtures/configuration remain unchanged. The separate
packaged-JAR Java caller also passes on all six combinations.

A first locale experiment showed that a process-wide locale change also alters
H2's database folding, so it cannot prove Java normalization independently. The
final test inspects the bound Criteria arguments instead; the documentation
explicitly retains database/collation-dependent Unicode behavior. This change
does not promise accent removal or universally identical locale rules.

[results.json](results.json) records counts and coverage. Native Maven logs,
Surefire XML, Checkstyle/coverage reports, the baseline failure run and caller
output remain under ignored `build/case-equality/`. Existing CI runs the same
matrix and retains module reports on failure; no GitHub run is claimed here.
Maven artifact signing and NVD scanning are skipped for compatibility verification;
Git commits remain signed. No artifacts are published.
