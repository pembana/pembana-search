# Pembana Search identity and documentation baseline

This housekeeping milestone starts from signed revision
`88a5d3d8123048178e1774ddc17d59e26f3f270a` and establishes
`com.pembana:pembana-search:1.0.0-SNAPSHOT` with packages under
`com.pembana.search`. Versioning now follows the requested Spring-style
snapshot/milestone/RC/release progression. No release or publication is performed.

## Identity changes

- Rename production/test packages and the annotation package to its correct
  spelling, `com.pembana.search.annotation`. Update ANTLR output paths,
  Boot imports metadata, reflection/import references, coverage exclusions,
  and the CI packaged-JAR caller together.
- Rename the test application and canonical corpus to
  `PembanaSearchTestApplication` and `PembanaSearchCompatibilityTest`.
  Map the original 113 method identities by class rename only; method names,
  queries, inputs, fixtures, assertions and outcomes are retained.
- Remove the unused, deprecated upstream default-package listener alias.
  No old-coordinate/package relocation or compatibility shim is provided.
- Set Pembana project/SCM/issue metadata and explicit Apache/MIT POM license
  metadata. Retain both original license files and upstream attribution.
- Remove inherited upstream OSSRH/release-plugin configuration, the automatic
  branch/push/PR version-bump script, and the unconfigured upstream Backstage
  catalog owner record. Disable Maven deployment by default.

This is intentionally a single artifact. A module split, independent query model,
new matching policies, framework/dependency upgrades, GitHub repository settings,
publishing credentials and external release actions remain separate work.

## Documentation and packaging

The root README is a current Java quickstart. Current guides cover installation,
MVC and direct JPA use, consumer scope composition, query semantics, configuration,
architecture, migration, testing, signing, NVD configuration, troubleshooting and
release preparation. Contributor/PR guidance and conduct contact are current.
The original upstream README/changelog are archived verbatim under `docs/upstream`.
Earlier reports and JSON evidence remain byte-identical historical records.

Handwritten API Javadoc has summaries, parameters/returns where applicable and
class author tags. Existing implicit public constructors are made explicit only
to attach Javadoc; their signatures/default initialization are unchanged.
All moved Java sources retain 2026 Apache headers and existing MIT attribution.
Generated grammar types remain compiled/tested but are excluded from public API
Javadoc. Maven fails on Javadoc errors and warnings on every supported JDK.

Binary, source and Javadoc JARs each carry byte-identical root `LICENSE`,
`LICENSE.txt` and `NOTICE` under `META-INF`. Embedded POM identity and Boot imports
name Pembana types, with no old package or default-package listener shipped.
CI verifies notice contents and retains generated API documentation alongside
native Maven evidence. It discovers the packaged JAR rather than hardcoding its version.

## Verification

The final Java 17/21/25 × H2/PostgreSQL matrix runs **148 tests per combination**:
113 original scenarios and 35 supplemental checks, with zero failures, errors or
skips. All six coverage gates, strict Javadoc builds, packaged Java callers and
metadata/dependency-report commands pass. H2 remains the Docker-free default;
PostgreSQL remains an explicit Testcontainers profile.

Source comparison removes comments/formatting, applies only the documented
package/class-name mapping, and accounts for the documented empty default
constructors. Production and test executable tokens otherwise match the prior
commit. Grammar/database inputs and historical evidence are byte-identical.
Resolved dependencies match the prior Stage 4 graph after changing only its root
coordinates. Initial matrix attempts exposed newer-JDK documentation warnings;
these were fixed as documentation/constructor declarations, not suppressed or
reported as query-test failures. All query tests passed in those exploratory runs.

The Java controller and direct-JPA examples compile against the packaged renamed
JAR, and their documented parser usage executes in a standalone Java process.
Current-guide local links and GitHub Actions YAML/Bash syntax are checked.
[results.json](results.json) records per-run counts, coverage, artifact/evidence
hashes, source/class mapping, and immutable historical inputs. Raw logs, reports,
JARs and example checks remain under ignored `build/identity/`.

Local verification uses macOS ARM64, BellSoft JDK 17.0.20, Temurin 21.0.10/25.0.3,
and OrbStack Docker. CI is configured but has not run for this local commit.
Maven artifact signing and the network Dependency-Check scan are skipped in
compatibility commands; Git commits remain signed separately. Nothing is pushed,
tagged, published or installed as a release by this milestone.
