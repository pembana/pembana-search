# Migrating to Pembana Search

This repository is a Java fork of Spring Search, not a drop-in binary replacement.
Its initial release is still under development; no Maven Central availability or
namespace/publishing-account registration is claimed.

| Previous import/build identity | Current identity |
| --- | --- |
| `com.sipios:spring-search:0.3.0` | `com.pembana:pembana-search-data-jpa:1.0.0-SNAPSHOT`, or `pembana-search-spring-boot-starter` for MVC |
| `com.sipios.springsearch.*` | `com.pembana.search.*` |
| `com.sipios.springsearch.anotation.SearchSpec` | `com.pembana.search.annotation.SearchSpec` |
| `SpringSearchApplication` test fixture | `PembanaSearchTestApplication` |
| `SpringSearchApplicationTest` corpus | `PembanaSearchCompatibilityTest` |
| Default-package deprecated `SyntaxErrorListener` | Removed; use `com.pembana.search.listeners.SyntaxErrorListener` |

Replace the dependency and imports together. There are no old-package aliases,
relocation artifacts, or binary compatibility shims. Remove the old artifact
from application dependencies to avoid retaining both annotation/resolver types.
Update fully qualified names in reflection, explicit imports, auto-configuration
exclusions and tooling. The Boot imports resource now names the Pembana class.
Normal Boot discovery does not require consumer component scanning.

## Migrating the single snapshot to modules

The earlier `com.pembana:pembana-search:1.0.0-SNAPSHOT` JAR becomes the
parent/aggregator POM. Replace that application dependency with
`pembana-search-spring-boot-starter` for Boot MVC/JPA integration, or
`pembana-search-data-jpa` for direct JPA. Use `pembana-search-core` alone for
Spring-independent parsing/model construction. All retain group `com.pembana`
and version `1.0.0-SNAPSHOT`; nothing is published and there is no relocation JAR.
Existing Java JPA/MVC imports remain available through their respective modules.

`CriteriaParser` now parses an immutable core tree before JPA translation. The
same translator accepts programmatic trees without concatenating query strings.
Matching defaults, conversion rules, original grammar, negative cases and error
metadata remain unchanged. No corrected/simple-search matching policy is enabled.
The one moved HTTP characterization method and all retained scenario identities
are mapped in the [module report](modules/README.md).

## Changes already made before this rename

- Handwritten production/tests moved from Kotlin to Java. Original scenarios are retained.
- The tested framework baseline moved to Spring Boot 4.1.1 with Hibernate 7;
  Java 17/21/25 and H2/PostgreSQL are verified. Java 8/Boot 3 are not supported promises.
- New annotation members opt into strict validation, allowed fields and limits.
  Their defaults preserve the old matching policy. Applications manually
  implementing the annotation must implement its complete member contract.
- Query diagnostics now offer structured codes while retaining existing 400
  reasons for syntax, blacklist and conversion errors. HTTP rendering is consumer-owned.

This identity change introduces no new matching policy, grammar, framework
upgrade, module split or application migration. The inherited operator quirks
and association limitations are documented in the [query reference](reference/query-language.md).

## Case-insensitive string equality correction

`caseSensitiveFlag=false` now applies to string equality and inequality, as it
already did to patterns. For `Ada`, `name:ADA` now matches and `name!ADA` no longer
matches. This intentionally changes false-flag queries; the original true default,
membership, ordering, typed conversion and null-check operations are unchanged.
Use `true` when retaining exact comparison is required, subject to the database's
collation. First-class global/per-property request options now exist as a separate opt-in
policy; omitting them keeps the annotation behavior. JSON search binding requires
`@SearchRequestBody`, preserving existing POST body handling.
See [case sensitivity](reference/case-sensitivity.md) for current behavior and the
implemented binding and recommended UI design. Historical baseline reports remain frozen.

## Historical evidence

Frozen Stage 0–4 reports retain old coordinates, class names, hashes, and upstream
references. Do not globally replace them. The original 113 method names and
assertions map to the new compatibility class by the table above; the identity
report records that mapping and equivalent outcomes. Upstream license/copyright
notices retain the original project name. Those references are provenance, not
current branding or installation advice.

## Consumer migration checks

1. Build/install the new unpublished snapshot locally or use an explicitly
   approved internal repository; do not assume a public snapshot repository exists.
2. Update imports/coordinates and any explicit resolver registration or exclusions.
3. Verify application queries and mandatory scope composition on its own schema.
4. Review strict validation choices separately; the rename does not turn it on.
5. Keep version/namespace adoption separate from future matching/model changes.
