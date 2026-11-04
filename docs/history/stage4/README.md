# Stage 4: query characterization and validation

Status: **verified locally on 2026-10-28**, starting from signed revision
`ec83698a7ca38ee21a5bb07c6f6c054c10ce30e1`. This is the validation and
characterization increment of Stage 4; it does not claim completion of the
requirements' future immutable query model, extension points, or new matching
policies. See the [query behavior guide](query-behavior.md) for the current
contract, migration examples, and remaining limitations.

## Change and compatibility

- Add opt-in strict validation, exact field allowlists, and character/grouping
  limits through four `SearchSpec` members. Defaults remain `false`, empty, zero,
  and zero respectively. Negative limits are configuration errors.
- Add `InvalidSearchQueryException` and stable `QueryErrorCode` values. Syntax,
  blacklist, and conversion errors retain status 400 and existing reason text
  while gaining structured metadata. Syntax diagnostics retain location text
  and expose line/column. Applications retain control of HTTP error rendering.
- Strict validation normalizes unknown paths, rejects invalid Boolean input,
  empty scalars, unsupported types, and field/operator combinations before SQL
  construction. It does not change matching, joins, date parsing, or null handling.
- Preserve the inherited grammar, all matching/conversion strategy sources,
  dependency versions, database profiles, CI matrix, original 113 test methods,
  and entity fixtures. The manual annotation test factory implements the new
  members and includes their defaults in its equality/hash contract. Original
  test queries, inputs, assertions, and identities are unchanged.

All four new Java source files carry 2026 Apache 2 headers, class author tags,
and Javadoc for every new class/method, including nested HTTP fixtures. Existing
source headers and upstream MIT attribution remain intact. New annotation
members and diagnostic-listener accessors also have Javadoc.

## Added coverage

`QueryBehaviorTest` adds 27 database-backed tests, executed unchanged on H2 and
Testcontainers PostgreSQL. They cover:

- Legacy Boolean coercion and silent Boolean ordering, text case asymmetry,
  percent/underscore LIKE wildcards, literal internal asterisks, operator
  precedence, empty membership, and nullable Boolean fields.
- Caller-owned tenant restrictions outside user OR expressions; explicitly
  sorted pages/counts; collection existence OR with multiple child rows; and
  nested association OR with an orphan. The last test records the existing
  implicit-inner-join limitation rather than rewriting joins.
- Strict scalar/list Boolean validation, unknown/basic-path navigation,
  numeric wildcards, enum/UUID ordering, invalid typed values, empty scalars,
  unsupported collection operations and association-valued scalar types.
- Successful strict typed queries, allowlist checks across all four grammar
  branches, blacklist precedence, inclusive size/depth boundaries, quoted
  parentheses, invalid limit configuration, and structured syntax locations.
- Real MockMvc request binding to the resolver, database execution, missing
  search, and HTTP 400 ProblemDetail metadata through consumer-owned advice.

Eleven characterization methods were also run in an isolated checkout of the
pre-change revision on both databases: **132 tests passed per database** (113
original, eight prior checks, eleven characterizations). The temporary test
source uses the old annotation API; valid queries that exercise strict mode in
the candidate use compatibility settings in that checkout. Their expressions,
fixtures, assertions, and outcomes are identical. This demonstrates that the
characterized matching/join/count behavior predates the validation changes.

## Verification results

| JDK | H2 2.4.240 | Testcontainers PostgreSQL 18.1 |
| --- | --- | --- |
| 17.0.20, BellSoft | 148 passed | 148 passed |
| 21.0.10, Temurin | 148 passed | 148 passed |
| 25.0.3, Temurin | 148 passed | 148 passed |

Each run separately discovers and executes **113 original + eight prior + 27
new tests**, with **zero failures, errors, or skips**. Original method identities
match the canonical inventory. Every packaged-JAR Java caller and dependency
report also succeeds. PostgreSQL containers are cleaned up after execution.

All six runs pass the unchanged Maven coverage gates. Aggregate instruction
coverage is **2,291/2,602 (88.05%)** and branch coverage **310/371 (83.56%)**.
Line coverage is **376/413 (91.04%)** on JDK 17/21 and **375/413 (90.80%)** on
JDK 25; compiler line mapping differs while instruction/branch counts agree.
Executable added/replaced production lines have **97/97 hits on JDK 17/21** and
**96/97 on JDK 25** (98.97–100%), exceeding the required changed-line 80%.
That audit intersects Git's zero-context changed lines plus all new production
lines with JaCoCo executable source lines; partially executed lines count as
hits, and branch coverage is reported separately. It does not imply every branch
or every query behavior is covered.

[results.json](results.json) records method outcomes, counts, environments,
coverage, changed-line evidence, unchanged-input fingerprints, and raw-evidence
hashes. Logs, Surefire XML, dependencies, and HTML/XML coverage remain under
ignored `build/stage4/`. Existing generated-code Javadoc and Maven repository
certificate warnings remain non-failing. Early exploratory runs exposed missing
test coverage and a test-controller null-specification mistake; the final tests
fix that consumer fixture explicitly and all final checks pass.

## Reproduce and next changes

Use the Maven wrapper with JDK 17, 21, or 25:

```sh
# Default H2; Docker is not needed.
./mvnw --batch-mode -Dgpg.skip=true -Ddependency-check.skip=true clean verify

# PostgreSQL; start a Docker-compatible engine first.
./mvnw --batch-mode -Dgpg.skip=true -Ddependency-check.skip=true -Dspring.profiles.active=postgresql clean verify
```

The existing six-combination GitHub Actions matrix automatically includes the
new tests and retains native reports even on failure. No CI scripts or Python
setup are added. Local verification used macOS ARM64/OrbStack; the GitHub workflow
has not run for this commit. Artifact signing and Dependency-Check are skipped
for compatibility verification; Git commits are signed separately. Nothing is
published.

Follow-on Stage 4 changes can introduce the immutable query model/registries and
extension points, then explicit word/pattern policies and association rewrites
with separate acceptance tests. Package/coordinate changes, module boundaries,
and release configuration remain separate gates. Preserve these compatibility
tests when intentionally correcting the documented legacy limitations.
