# Pembana Search™ documentation

## Current guides

| Guide | Audience and content |
| --- | --- |
| [Getting started](getting-started.md) | Application developers: install, MVC binding, direct JPA and consumer scopes |
| [Query reference](reference/query-language.md) | Grammar, typed operators, matching quirks, validation, errors and association limits |
| [Collection handling](reference/collections.md) | Per-query collection searches, same-child conditions, ranges, and optional child relations |
| [Optional to-one handling](reference/optional-to-one.md) | Keep matching records when a related record is missing |
| [Case sensitivity](reference/case-sensitivity.md) | Global/property options, GET/JSON binding, compatibility, validation and form UX |
| [Configuration/API reference](reference/configuration.md) | Annotation defaults, public entry points, auto-configuration and dependencies |
| [Architecture](architecture.md) | Core model, JPA translation, optional starter and dependency boundaries |
| [Case options verification](case-options/README.md) | Unchanged defaults, 232-scenario matrix and global/property binding evidence |
| [Module verification](modules/README.md) | Retained scenario mapping, new equivalence tests and module acceptance evidence |
| [Development](development.md) | Contributors: JDKs, databases, CI artifacts, coverage, signing, NVD and troubleshooting |
| [Spring Checkstyle](checkstyle/README.md) | Build-time style enforcement, formatting, compatibility exceptions and verification |
| [Search acceptance](search-acceptance.md) | Adopted baseline, source/binary checks, JDBC parity, and runtime/database evidence |
| [Feature requirements](requirements/pembana-search/README.md) | Accepted compatibility contract and TEXT/FIELDS/ASSOC delivery boundaries |
| [Migration](migration.md) | Coordinate/package changes and intentional incompatibilities |
| [Releasing](releasing.md) | JReleaser staging, signing, snapshots/releases, workflow secrets and version progression |
| [Identity verification](identity/README.md) | Evidence for the mechanical rename and packaged artifacts |
| [Upstream author provenance](provenance/upstream-authors.md) | Per-file author credits, supporting Git commits, identity handling and exceptions |

The root [README](../README.md) is the short Java quickstart. Generated API HTML
is in each module’s `target/reports/apidocs/` after packaging; a Javadoc JAR is attached to the build.
