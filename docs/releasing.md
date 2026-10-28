# Versioning and release preparation

Current coordinates: `com.pembana:pembana-search:1.0.0-SNAPSHOT`.
No artifact has been published. Group naming follows Pembana's existing project;
this does not assert registry ownership or an approved publication account.

## Spring-style versions

Develop toward 1.0 using `1.0.0-SNAPSHOT`. If pre-releases are needed, use
`1.0.0-M1`, `1.0.0-M2`, then `1.0.0-RC1`, followed by `1.0.0` for the first stable
release. Subsequent development increments the appropriate major/minor/patch
component and resumes a `-SNAPSHOT` version, for example `1.0.1-SNAPSHOT` for the
next maintenance iteration. Record intentional API/behavior changes in the
changelog and migration guide; do not imply 1.0 stability for snapshots.

## Current publication safeguards

- No publishing workflow, upstream OSSRH destination, or upstream release plugin
  configuration remains. The inherited branch/push/version-bump script is removed.
- `maven.deploy.skip=true` disables deployment by default. There is no configured
  replacement distribution repository. Do not override this as an informal release process.
- Local `install` is available for development. `package`/`verify` do not publish.
- Git commit signing and Maven artifact signing remain distinct.

## Gate before the first publication

1. Confirm publication namespace ownership, repository/account, access controls,
   signing keys, credentials/secrets and an approved release workflow.
2. Decide the release's supported APIs and whether the planned core/JPA/starter
   boundaries are implemented; publish only artifacts that actually exist.
3. Verify all original/supplemental scenarios and the declared JDK/framework/database
   matrix, perform the vulnerability review, and explain unresolved findings.
4. Verify binaries, sources and Javadoc contain licenses/notices and accurate POM
   identity/SCM metadata. Confirm no test/database or Kotlin runtime is required
   by consumers and no old upstream package is unintentionally shipped.
5. Compile documented Java usage, validate documentation links, and update the
   changelog, migration notes, compatibility table and version references.
6. Review source changes and signed commits. Create release tags and enable
   publishing only as explicitly authorized release work; do not rewrite history.

The identity milestone performs build/metadata checks but does not claim these
publication gates are complete. No Maven relocation POMs, snapshots repository,
package mirrors, GitHub release, tag or published documentation site are created.
