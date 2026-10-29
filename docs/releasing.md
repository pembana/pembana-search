# Versioning and Maven publication

Current development version: `1.0.0-SNAPSHOT`, group `com.pembana`.
The release process follows `image-filters-4j`: Maven stages artifacts locally,
then JReleaser signs and deploys the staging repository. JReleaser is pinned to
**1.22.0**, matching that project, with its current `signing.pgp` configuration.

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

A snapshot is not a Maven Central release. The library version determines the
active deployer; there is no workflow version override or automatic version bump.
The process deploys Maven artifacts only: it does not create Git tags, GitHub
releases, commits or documentation sites. Use `jreleaser:deploy`, not
`jreleaser:full-release`, for this process.

## Repository configuration

Before the first actual publication:

1. Verify ownership of `com.pembana` in the Sonatype Central Portal and create a
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

The workflow uses the built-in GitHub token with `contents: read` for repository
metadata. It does not require a write-enabled personal access token. Signing
secrets are exposed only to the JReleaser step. Git commit/tag signing and Maven
artifact signing are separate; the `publication` profile disables Maven's GPG
execution because JReleaser signs staged artifacts itself.

## GitHub Actions process

Open **Actions → Maven publication → Run workflow** on the intended revision.
Leave **dry-run** enabled for the first run.

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

As in compatibility CI, the workflow skips the network Dependency-Check scan.
Complete the vulnerability review separately before an actual release; a passing
publication workflow is not evidence of a clean vulnerability scan.

## Local staging and dry runs

Use JDK 21 for Maven. Default builds retain `maven.deploy.skip=true` and have no
remote deployment repository. The explicit `publication` profile changes Maven's
`deploy` destination to a **local file repository shared by all modules** and
uses `deployAtEnd`; source and Javadoc attachments retain the existing strict
checks. It does not upload anything by itself.

```sh
# Runs H2 tests and the normal gates, then stages all modules locally.
./mvnw --batch-mode --no-transfer-progress -Ppublication \
  -Ddependency-check.skip=true clean deploy
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

## Spring-style release preparation

Develop toward 1.0 using `1.0.0-SNAPSHOT`. Prepare milestones `1.0.0-M1`,
`1.0.0-M2`, then `1.0.0-RC1` before `1.0.0`. For example, update the whole reactor
on a release-preparation branch:

```sh
./mvnw --batch-mode --no-transfer-progress \
  org.codehaus.mojo:versions-maven-plugin:2.20.1:set \
  -DnewVersion=1.0.0-M1 -DgenerateBackupPoms=false
```

Review the parent and child versions, update the README version badge/current
version, changelog, migration notes and compatibility documentation, then create
a signed commit. Complete the API review, consumer-example verification and
vulnerability review before publishing. Verify packaged identity, attribution,
and supported runtime/database scenarios with the existing CI reports.

Run the manual workflow first as a dry run, then publish the approved revision.
After confirming publication, create any signed release tag and GitHub release
as separate release work. Advance the reactor to the next development snapshot
in a signed commit: after a milestone this may return to `1.0.0-SNAPSHOT`; after
`1.0.0`, use `1.0.1-SNAPSHOT` for maintenance development. Do not rewrite history
or reuse an already-published release version.

## Configuration verification

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
- [JReleaser Maven plugin and dry-run mode](https://jreleaser.org/guide/latest/tools/jreleaser-maven.html)
- [PGP signing configuration](https://jreleaser.org/guide/latest/reference/signing.html)
