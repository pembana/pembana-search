# Contributing to Pembana Search

Use issues and pull requests at https://github.com/pembana/pembana-search.
Read the [Code of Conduct](CODE_OF_CONDUCT.md) and [development guide](docs/development.md).

1. Branch from `main`; use a descriptive branch name (`codex/` for agent work).
2. Keep behavior changes separate from mechanical renames and framework upgrades.
3. Use the Maven wrapper with JDK 17, 21, or 25. H2 is the default; PostgreSQL is
   an explicit Spring profile backed by Testcontainers.
4. Add meaningful regression tests for fixes/features. Keep all 113 upstream
   methods and their assertions, with the documented identity mapping after renaming.
5. Run relevant checks and preserve native Maven reports. CI verifies H2 and
   PostgreSQL on all three JDKs; see the development guide for commands.
6. Document API and intentional semantic changes, compatibility limits, and
   migration steps. Never rewrite historical baseline evidence as current output.
7. Sign commits (`git commit -S`) and verify them (`git verify-commit HEAD`).
   Prefer Conventional Commit titles for new contributions.

New Java source files require a **2026 Apache License 2.0 header**, meaningful
class and method Javadoc, and class `@author` tags. Use Gunnar Hillert for the
current fork contributions and preserve existing upstream MIT attribution.
Author tags belong on types; method Javadoc should explain parameters, return
values, failure modes, and non-obvious behavior. Inherited overrides can use
`{@inheritDoc}` when the documented contract is accurate. Do not add boilerplate
tests that merely repeat implementation details.

Existing Maven coverage gates require 80% instructions and 75% branches, excluding
only generated parser classes. New/materially changed production code must meet
80% changed-line coverage, with meaningful failure-path coverage. Describe
remaining gaps rather than excluding difficult code to make checks pass.

Do not add custom build/comparison scripts or Python setup where Maven and
GitHub Actions suffice. Publishing and release credentials are configured in a
separate release milestone; no automatic push, tag, PR, or deployment is part
of a local build. See the [release checklist](docs/releasing.md).
