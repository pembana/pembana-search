# Contributing to Pembana Search

Use issues and pull requests at https://github.com/pembana/pembana-search.
Read the [Code of Conduct](CODE_OF_CONDUCT.md) and [development guide](docs/development.md).

1. Branch from `main`; use a descriptive branch name (`codex/` for agent work).
2. Keep behavior changes separate from mechanical renames and framework upgrades.
3. Use the Maven wrapper with build JDK 21 and Java 17 release targeting. H2 is the default; PostgreSQL is
   an explicit Spring profile backed by Testcontainers.
4. Add meaningful regression tests for fixes/features. Keep all 113 upstream
   methods and their assertions, with the documented identity mapping after renaming.
5. Run relevant checks and preserve native Maven reports. CI verifies H2 and
   PostgreSQL on Java 17/21/25 runtimes while building on JDK 21; see the development guide for commands.
6. Document API and intentional semantic changes, compatibility limits, and
   migration steps. Never rewrite historical baseline evidence as current output.
7. Sign commits (`git commit -S`) and verify them (`git verify-commit HEAD`).
   Prefer Conventional Commit titles for new contributions.

New Java source files require a **2026 Apache License 2.0 header**, meaningful
class and method Javadoc, and class `@author` tags. Credit verified contributors
(Gunnar Hillert for the current fork work). Converted Spring Search files list
verified individual upstream authors; omit the generic `Spring Search contributors`
author tag when those credits are established. Use a collective tag only while
individual attribution remains unresolved. Preserve existing MIT attribution,
and do not infer each file's authors from the project-wide copyright notice.
The [verified upstream author mapping](docs/provenance/upstream-authors.md)
records individual credits, identity evidence, and exceptions for extracted or
fork-only helper classes. Extend it when adding newly verified upstream credits.
Author tags belong on types; method Javadoc should explain parameters, return
values, failure modes, and non-obvious behavior. Inherited overrides can use
`{@inheritDoc}` when the documented contract is accurate. Do not add boilerplate
tests that merely repeat implementation details.

New Java types also require JSpecify `@NullMarked` coverage and `@Nullable` where
null is intentional. Maven checks both production and test code with NullAway
automatically (`checking=tests`). All handwritten production and test packages
are `@NullMarked`; do not introduce unmarked regions or suppressions to bypass
failures. See
[nullability checks](docs/development.md#nullability-checks) for the checked scope
and the OpenJDK 21.0.8+ build requirement.

Existing Maven coverage gates require 80% instructions and 75% branches, excluding
only generated parser classes, enforced independently in each module. New/materially changed production code must meet
80% changed-line coverage, with meaningful failure-path coverage. Describe
remaining gaps rather than excluding difficult code to make checks pass.

Do not add custom build/comparison scripts or Python setup where Maven and
GitHub Actions suffice. Publishing and release credentials are configured in a
separate release milestone; no automatic push, tag, PR, or deployment is part
of a local build. See the [release checklist](docs/releasing.md).

Spring JavaFormat Checkstyle 0.0.48 runs during Maven `validate` on production and
test Java sources. Run `./mvnw validate` from the reactor root for a quick style check, and use
`./mvnw io.spring.javaformat:spring-javaformat-maven-plugin:0.0.48:apply` for
mechanical formatting. Review the diff: formatting does not fix every import,
qualification, or semantic-style rule. The [style guide](docs/checkstyle/README.md)
explains the narrow compatibility exceptions; retain original assertion semantics
and upstream license attribution.
