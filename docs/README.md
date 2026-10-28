# Pembana Search documentation

## Current guides

| Guide | Audience and content |
| --- | --- |
| [Getting started](getting-started.md) | Application developers: install, MVC binding, direct JPA and consumer scopes |
| [Query reference](reference/query-language.md) | Grammar, typed operators, matching quirks, validation, errors and association limits |
| [Configuration/API reference](reference/configuration.md) | Annotation defaults, public entry points, auto-configuration and dependencies |
| [Architecture](architecture.md) | Current data flow, single-artifact boundaries, future module plan |
| [Development](development.md) | Contributors: JDKs, databases, CI artifacts, coverage, signing, NVD and troubleshooting |
| [Migration](migration.md) | Coordinate/package changes and intentional incompatibilities |
| [Releasing](releasing.md) | Spring-style version progression and the unpublished release checklist |
| [Identity verification](identity/README.md) | Evidence for the mechanical rename and packaged artifacts |

The root [README](../README.md) is the short Java quickstart. Generated API HTML
is in `target/reports/apidocs/` after packaging; a Javadoc JAR is attached to the build.

## Historical evidence

The following reports are frozen records of earlier commits. Their coordinates,
class names, JDK/framework versions, commands, and hashes describe those commits;
use the guides above with the current checkout.

- [Upstream baseline and canonical 113-scenario inventory](baseline/README.md)
- [Stage 1: production Java port](stage1/README.md)
- [Stage 2: Java tests and Kotlin removal](stage2/README.md)
- [Stage 3: framework/database modernization](stage3/README.md)
- [Testcontainers transition](testcontainers/README.md)
- [Stage 4: query characterization and validation](stage4/README.md)
- [Archived upstream documentation](upstream/index.md)
