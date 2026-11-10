# Contributing to Pembana Search™

## Code of Conduct

Please read the [Code of Conduct](CODE_OF_CONDUCT.md).

## Fork the Repository

Use issues and pull requests at https://github.com/pembana/pembana-search.
Please also read the [development guide](docs/development.md).

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

## Maintain a linear commit history

The project uses fast-forward merges exclusively on `main`. Before opening a pull request, **rebase your branch onto the latest `main`** and check that the commits in your branch form a straight line.

To inspect the commit graph, run

```sh
git log --graph --pretty=oneline
```

If you often use this command, you can create a global `git logg` alias.

```sh
git config --global alias.logg 'log --graph --pretty=oneline'
```

In a linear history, each commit appears on the same vertical line.

```text
* 1dcf61772cdd2ed0362753db77ab28a868b8318d fix!: apply case sensitivity consistently to string comparisons
* 3bd4c450d91d903a871177d0e0fcbdf65f194bb9 feat!: enable strict validation and query limits by default
* 42e1099d27e09a57a8d48d4d24b435fddb1a5cc3 fix: correct Boolean operator precedence for queries
* a19532968259b759374e6c968f605b5064ac11c6 feat: add opt-in optional to-one association handling
* a7c86eb8fb785f82fa7b448bf1ec75adcc584bf8 feat: add opt-in converters and predicate extensions
* 246067ae76fe1c865c67f611faeb483bd54ced44 feat: add opt-in per-query field profiles
* 2249385187621fe89f5e2be42ade6a6613a345c3 feat: add opt-in simple search with per-query modes
* 2c8d3bc0f3978efbff4e90b13888d3409c87fd78 feat: implement exact Long value handling and validation
```

If the graph shows a merge commit among the commits you plan to submit, rebase your branch onto `main` and check the graph again.

## Code Style

The project uses the [Spring Framework](https://github.com/spring-projects/spring-framework/blob/main/CONTRIBUTING.md#source-code-style)
style guide. The Spring JavaFormat Checkstyle Maven plugin enforces formatting and style rules.

The Maven Checkstyle Plugin runs during Maven `validate` on production and test Java sources. Run `./mvnw validate` from
the reactor root for a quick style check and use
`./mvnw io.spring.javaformat:spring-javaformat-maven-plugin:0.0.48:apply` for
mechanical formatting. Review the diff: formatting does not fix every import,
qualification, or semantic-style rule. The [style guide](docs/checkstyle/README.md)
explains the narrow compatibility exceptions; retain original assertion semantics
and upstream license attribution.

## Git Commit Messages

Follow Chris Beams’ [guidelines for writing Git commit messages](https://chris.beams.io/posts/git-commit).

For dependency upgrades, list each updated dependency or build plugin by name
and include its old and new versions in the commit message body, for example
`Spring Boot starter parent: 4.1.1 -> 4.1.2`. Include the validation performed.
