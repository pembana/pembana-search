# Versioning and Maven publication

The root [POM](../pom.xml) records the current development version for group
`com.pembana.search`.
The release process follows `image-filters-4j`: Maven stages artifacts locally,
then JReleaser signs and deploys the staging repository. JReleaser is pinned to
**1.27.0**, using the `signing.pgp` configuration.

## Artifacts and destinations

The complete reactor is published together:

- `pembana-search`: parent/aggregator POM.
- `pembana-search-core`: immutable query model and expression parser.
- `pembana-search-data-jpa`: JPA translation and legacy Java entry points.
- `pembana-search-spring-boot-starter`: Boot/MVC integration.

Each library includes its binary, sources and Javadoc JARs. The JPA module also
attaches its shared `tests` fixture JAR; this classifier is for test consumers and
is not a runtime dependency. Apache and retained upstream MIT notices remain in
all JARs. Module POMs contain explicit descriptions and inherit developer/license
metadata from the parent.

| Version | JReleaser deployer | Destination |
| --- | --- | --- |
| `*-SNAPSHOT` | `nexus2.snapshot-deploy` | `https://central.sonatype.com/repository/maven-snapshots/` |
| Release, milestone or RC | `mavenCentral.sonatype` | `https://central.sonatype.com/api/v1/publisher` |

The library version determines the active deployer. In artifact-only mode the
workflow reads the committed version. In full-release mode it first prepares a
release commit with the requested version.

## Repository configuration

Before the first actual publication:

1. Verify ownership of `com.pembana.search` in the Sonatype Central Portal and create a
   publishing user token with access to that namespace.
2. Create the GitHub environment **`maven-central`**. Configure its permitted
   release branches and any desired required reviewers in repository settings.
3. Add the following environment secrets. Names deliberately retain the reference
   project's GPG and snapshot credential conventions.

| Secret | Value |
| --- | --- |
| `JRELEASER_GPG_PUBLIC_KEY` | Current ASCII-armored public signing key, including current expiry metadata |
| `JRELEASER_GPG_SECRET_KEY` | Matching ASCII-armored secret signing key |
| `JRELEASER_GPG_PASSPHRASE` | Signing key passphrase |
| `JRELEASER_MAVENCENTRAL_SONATYPE_USERNAME` | Central Portal user-token username for releases |
| `JRELEASER_MAVENCENTRAL_SONATYPE_PASSWORD` | Central Portal user-token password for releases |
| `JRELEASER_NEXUS2_USERNAME` | Central snapshot repository user-token username |
| `JRELEASER_NEXUS2_PASSWORD` | Central snapshot repository user-token password |

The same Portal token may be used for both credential pairs if authorized for
both destinations. A release needs its active deployer's credentials; configure
both pairs when publishing both snapshots and releases. Publish the public PGP
key to a Maven Central-supported keyserver, verify it is current, and retain the
matching private key securely. Renewing a key locally also requires refreshing
exported keys in GitHub secrets and local JReleaser settings.

Artifact-only publication uses the built-in GitHub token with `contents: read`
for repository metadata. The `publication` profile disables Maven's GPG execution
because JReleaser signs the artifacts.

For full releases, also create the `release-git` environment, restrict it to
`main`, and configure the following variables and secrets:

| Type | Name | Value |
| --- | --- | --- |
| Variable | `RELEASE_GIT_NAME` | Name of the release signing identity |
| Variable | `RELEASE_GIT_EMAIL` | Verified GitHub email matching the GPG identity |
| Variable | `RELEASE_GPG_FINGERPRINT` | Fingerprint of the release identity's Git signing key |
| Secret | `RELEASE_GPG_PRIVATE_KEY` | ASCII-armored private Git signing key |
| Secret | `RELEASE_GPG_PASSPHRASE` | Passphrase for the Git signing key, if set |
| Variable | `RELEASE_APP_CLIENT_ID` | Client ID of the release GitHub App |
| Secret | `RELEASE_APP_PRIVATE_KEY` | Private key of the release GitHub App |

Register the Git signing public key with the release identity on GitHub. Use a
separate key from the Maven artifact key. Install the GitHub App on this
repository with Contents write permission. Check that the App can fast-forward
`main` and create signed release tags under the repository rules, and protect
published tags against updates and deletion. Keep any ruleset bypass limited
to the release App and the applicable refs. The final job uses the built-in
token with Contents write permission to post the GitHub release.

## Artifact-only publication

Open **Actions → Maven publication → Run workflow** on the intended revision.
Leave both version inputs blank and keep **dry-run** enabled for the first run.

1. The workflow calls the existing Maven CI workflow, running H2 and PostgreSQL
   across Java 17, 21 and 25. Maven itself runs on Java 21 and targets Java 17.
   PostgreSQL uses Testcontainers; the ordinary local build still defaults to H2.
2. After that matrix passes, the publication job builds and verifies the reactor
   on Java 21 and stages it under root `target/staging-deploy`.
3. JReleaser runs once at the reactor root (`-N`), checks staged POMs, signs the
   artifacts and prepares the selected deployment. With dry-run enabled, remote
   deployment operations are skipped. Dry runs still need usable signing keys
   and configuration; they are not a credential-free build mode.
4. Inspect the reports. To publish, rerun the workflow on the same approved
   revision with **dry-run unchecked**. This uploads to the destination selected
   by the committed version. Release versions on Central are immutable.

Publication runs are serialized and do not cancel an in-progress deployment.
The CI corpus and publication build retain their test/coverage/Checkstyle reports
even on failure. Publication evidence also includes the revision, version, build
log, `target/jreleaser/trace.log` and `target/jreleaser/output.properties`.
Reports are kept for 30 days. Never upload local secret-key exports or settings
files as artifacts.

Publication and compatibility workflows do not activate the opt-in `security`
profile. Review the separate nightly/manual **Dependency security scan** reports
for the intended release revision before publishing; trigger it manually when
necessary. A passing publication workflow is not evidence of a clean vulnerability
scan. See the [security workflow and cache guide](development.md#dependency-check-and-nvd).

## Local staging and dry runs

Use JDK 21 for Maven. Default builds retain `maven.deploy.skip=true` and have no
remote deployment repository. The explicit `publication` profile changes Maven's
`deploy` destination to a **local file repository shared by all modules** and
uses `deployAtEnd`; source and Javadoc attachments retain the existing strict
checks. It does not upload anything by itself.

```sh
# Runs H2 tests and the normal gates, then stages all modules locally.
./mvnw --batch-mode --no-transfer-progress -Ppublication \
  clean deploy
```

The profile's default goal is also `deploy`, as in the reference project. Prefer
an explicit `clean deploy` to avoid leftover versions or stale staging files.
Do not use `-N` for staging: the complete reactor must reach one root repository.
The local Maven repository receives the normal `install` artifacts as part of
this lifecycle.

Provide the documented secrets through environment variables or your untracked
JReleaser settings file, then run:

```sh
# Checks and prepares the upload, but does not perform remote deployment.
./mvnw --batch-mode --no-transfer-progress -N -Ppublication \
  -Djreleaser.dry.run=true jreleaser:deploy
```

JReleaser can load user-level `~/.jreleaser/config.properties`; check that it does
not contain stale credentials or expired key exports. Use an explicit
`-Djreleaser.settings.file=/path/to/private/settings.yml` to select a different
settings file. Do not commit it. A dry run may still make read-only requests,
such as checking whether the public signing key is available.

After reviewing the staged revision and reports, this command **publishes**:

```sh
./mvnw --batch-mode --no-transfer-progress -N -Ppublication jreleaser:deploy
```

Do not run it as part of routine development. No real publication is performed
when adding or testing this configuration.

## Full release

1. Prepare the `## Unreleased` section in [CHANGELOG.md](../CHANGELOG.md) on
   `main`. Start with a short summary. Use the level-three headings shown
   in the changelog for upgrade instructions, features, fixes, documentation,
   dependency upgrades, and verified contributors. Link entries to issues,
   PRs, or commits when available. Use relative `docs/` links for repository
   guides so the release notes can link to the tagged version.
2. Dispatch **Maven publication** on `main` with both versions filled in.
   For a milestone such as `1.0.0-M2`, use `1.0.0-SNAPSHOT` as the next
   snapshot. For `1.0.0`, use `1.0.1-SNAPSHOT`. The current snapshot must
   have the same base version as the release.
3. Keep **dry-run** enabled for the first run. Review the Markdown in the
   run summary or the `prepared-release-*` artifact, the recorded source
   revision, the compatibility reports, and the dependency scan for that
   revision. If `main` advances, review a new dry run before publishing.
4. Dispatch the same inputs with **dry-run** unchecked. The workflow verifies
   the exact signed release commit. JReleaser signs and uploads the bundle in
   its `UPLOAD` stage. The workflow confirms validation, then atomically pushes
   both commits and the signed `v<version>` tag. JReleaser runs its `PUBLISH`
   stage with the same deployment ID. Once Central reports `PUBLISHED`, the
   workflow posts the rendered Markdown against that tag. Milestones and release
   candidates appear as GitHub prereleases.

### Recovering a full release

- Use **Re-run failed jobs** on the original run. Successful preparation and
  upload jobs retain their artifact and deployment IDs. If Central already
  accepted the publish request, the workflow waits for or recognizes
  `PUBLISHED` and continues to GitHub.
- If JReleaser fails during upload or times out waiting for validation, check
  its log and Central Portal for the deployment before retrying. JReleaser logs
  the ID on receipt and writes it to `output.properties` after validation. Drop
  any unpublished orphan from the failed attempt before re-running the job.
- If `main` advanced or the tag conflicts before the push, the atomic push
  leaves both refs as they were. Review the conflict and discard the unused
  Central deployment before starting a fresh release from the intended tip.
- If validation reports `FAILED`, correct the reported issue before starting
  a fresh release. If publication fails after the Git push, keep the signed
  tag and inspect the original deployment in Central Portal using the recorded
  ID. Use the original signed bundle if Central requires a replacement upload.

## Original publication configuration verification

On 2026-10-29, local staging of `1.0.0-SNAPSHOT` passed all 232 H2 tests and the
normal formatting, nullability, Javadoc and coverage checks. The shared staging
repository contained four POMs and ten JARs. A temporary clone was versioned to
`1.0.0-M1`, built and staged with the same checks and 232 passing tests; the main
checkout stayed at its snapshot version.

Both JReleaser dry runs passed with disposable PGP keys and placeholder deployment
credentials. The snapshot selected `nexus2.snapshot-deploy`; the milestone selected
`mavenCentral.sonatype` and prepared its Central bundle. Effective-POM checks
confirmed default deployment remains disabled, publication staging is file-only
and shared across modules, and the JReleaser plugin is not inherited. Workflow
YAML parsed successfully. No remote publication, tag, GitHub release or GitHub
Actions execution is claimed by these local checks. The workflow reuses the
existing six-combination compatibility job before publication; the matrix was not
rerun locally for this build-configuration-only change.

Local logs and model checks are retained under ignored `build/release/`, with
JReleaser snapshot output under ignored `target/jreleaser/`.

## References

- [JReleaser Maven Central and snapshot publishing](https://jreleaser.org/guide/latest/examples/maven/maven-central.html)
- [JReleaser staged deployments](https://jreleaser.org/guide/latest/reference/deploy/maven/maven-central.html#_staged_deployments)
- [JReleaser Maven plugin and dry-run mode](https://jreleaser.org/guide/latest/tools/jreleaser-maven.html)
- [PGP signing configuration](https://jreleaser.org/guide/latest/reference/signing.html)
- [Central Portal deployment API](https://central.sonatype.org/publish/publish-portal-api/)
