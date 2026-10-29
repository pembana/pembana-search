# Verified upstream author credits

Verified on **2026-10-28** against the imported Spring Search revision
`c4b404dc6cf22b540d3e7f6211e557f5d83b6c61` and its complete local Git ancestry.
This attribution update starts from Pembana revision
`8477fb68316e0d477ae8b9d43577de808adc97f6`.

## Attribution policy

Individual `@author` tags are added to **34 converted Java classes**, retaining
`Gunnar Hillert` for the Java port/fork work and removing the redundant collective
`Spring Search contributors` tag. Credits come from the
upstream source file's non-merge commit authors and explicit `Co-authored-by`
trailers. Rename history is followed. These are recorded upstream contributors,
including maintainers/refactorers, not an assertion that every listed person
created the class originally or owns its copyright. Committers are not
substituted for authors, and commits after the imported revision are excluded.

The mapping and commit evidence are in [upstream-authors.json](upstream-authors.json).
It contains 35 file dispositions, a shared commit table, contributor-to-commit
links, identity-resolution evidence, and the helper exception below. Personal
email addresses are not republished in that record.

### Identity handling

- `MollardMichael` is credited as **Michael Mollard**. The
  [public profile](https://github.com/MollardMichael) supplies that display name,
  and [upstream commit 905f77b](https://github.com/theodo-fintech/spring-search/commit/905f77b7eb47dd26a2d055c75fa377e3522b16e5)
  records Michael Mollard with GitHub author login `MollardMichael` (account ID
  10924873). That commit is an ancestor of the imported revision.
- `lucboussant-roux` is credited as **Luc Boussant-roux**, the full spelling
  recorded under the same exact author email in the pinned ancestry. The two
  supporting commits are recorded in the JSON. A name can be corroborated by a
  merge commit's author identity without giving that merger file-level credit.
- **Thibault F**, **Mahdi Lazraq**, **HamidReza Ireh**, **Diego Corredor**, and
  **Vincent Escoffier** are retained as recorded in upstream Git; abbreviated or
  unusually capitalized names are not expanded or corrected by inference.
- `vincentescoffier` and `fernandorubio` remain recorded handles, labeled
  `(upstream Git author)` in Javadoc. Full names are not inferred from email
  addresses. Vincent Escoffier and vincentescoffier are not silently merged merely
  because their names resemble each other or occur together in a co-author trailer.

Woody Rousseau's existing project-wide copyright notice remains intact. It is
not converted into a universal per-class author claim when the file history does
not support that attribution.

### Extracted class and Java-only helper

`NullSpecification` was a second class in upstream `SpecificationsBuilder.kt`,
not an independent file. Its retained class lines **32–36** are audited with
whitespace/move/copy-aware blame. They identify **Thibault F** and **Michael
Mollard** (recorded as MollardMichael), rather than every author of unrelated
builder methods. The class's spelling correction is part of that evidence.

`SearchTestFixtures` has no directly converted upstream class. It is Java-only
annotation/set adaptation code introduced during the port. It credits
Gunnar Hillert for the fork implementation and retains its license notices; no
collective or individual upstream class author is invented from its callers.
New fork-only API/validation classes and package documentation receive no
unrelated upstream tags.

## Per-file mapping

Paths below are relative to `src/`; the JSON records full current and upstream
paths and supporting commit hashes for each tag.

| Converted Java source | Recorded upstream contributor credits |
| --- | --- |
| `main/java/com/pembana/search/CriteriaParser.java` | Thibault F; Michael Mollard; Luc Boussant-roux; vincentescoffier (upstream Git author) |
| `main/java/com/pembana/search/NullSpecification.java` | Thibault F; Michael Mollard |
| `main/java/com/pembana/search/QueryVisitorImpl.java` | Thibault F; Michael Mollard; Luc Boussant-roux; Mahdi Lazraq; vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/SearchCriteria.java` | Thibault F; Michael Mollard; Mahdi Lazraq; vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/SearchOperation.java` | Thibault F; Michael Mollard; vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/SpecificationImpl.java` | Thibault F; Michael Mollard; Luc Boussant-roux; Mahdi Lazraq; vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/SpecificationsBuilder.java` | Thibault F; Michael Mollard; Luc Boussant-roux; Mahdi Lazraq |
| `main/java/com/pembana/search/annotation/SearchSpec.java` | Thibault F; Michael Mollard; Luc Boussant-roux; Mahdi Lazraq; Vincent Escoffier; vincentescoffier (upstream Git author) |
| `main/java/com/pembana/search/configuration/ResolverConf.java` | Thibault F |
| `main/java/com/pembana/search/configuration/SearchSpecificationResolver.java` | Thibault F; Michael Mollard; Luc Boussant-roux; Mahdi Lazraq |
| `main/java/com/pembana/search/listeners/SyntaxErrorListener.java` | vincentescoffier (upstream Git author) |
| `main/java/com/pembana/search/strategies/BooleanStrategy.java` | Michael Mollard; vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/strategies/CollectionStrategy.java` | vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/strategies/DateStrategy.java` | Michael Mollard; Luc Boussant-roux; Mahdi Lazraq; vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/strategies/DoubleStrategy.java` | Michael Mollard; Luc Boussant-roux; Mahdi Lazraq; vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/strategies/DurationStrategy.java` | Michael Mollard; Luc Boussant-roux; HamidReza Ireh; Mahdi Lazraq; vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/strategies/EnumStrategy.java` | Michael Mollard; Luc Boussant-roux; HamidReza Ireh; vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/strategies/FloatStrategy.java` | Michael Mollard; Luc Boussant-roux; Mahdi Lazraq; vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/strategies/InstantStrategy.java` | Michael Mollard; Luc Boussant-roux; fernandorubio (upstream Git author); Mahdi Lazraq; vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/strategies/IntStrategy.java` | Michael Mollard; Luc Boussant-roux; Mahdi Lazraq; vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/strategies/LocalDateStrategy.java` | Michael Mollard; Luc Boussant-roux; HamidReza Ireh; Mahdi Lazraq; vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/strategies/LocalDateTimeStrategy.java` | Michael Mollard; Luc Boussant-roux; HamidReza Ireh; Mahdi Lazraq; vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/strategies/LocalTimeStrategy.java` | Michael Mollard; Luc Boussant-roux; HamidReza Ireh; Mahdi Lazraq; vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/strategies/ParsingStrategy.java` | Michael Mollard; Luc Boussant-roux; HamidReza Ireh; Diego Corredor; fernandorubio (upstream Git author); Mahdi Lazraq; vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/strategies/StringStrategy.java` | Michael Mollard; Luc Boussant-roux; Mahdi Lazraq; vincentescoffier (upstream Git author); Vincent Escoffier |
| `main/java/com/pembana/search/strategies/UUIDStrategy.java` | Michael Mollard; Diego Corredor; vincentescoffier (upstream Git author); Vincent Escoffier |
| `test/java/com/pembana/search/Author.java` | vincentescoffier (upstream Git author) |
| `test/java/com/pembana/search/AuthorRepository.java` | Thibault F; Michael Mollard; vincentescoffier (upstream Git author) |
| `test/java/com/pembana/search/Book.java` | vincentescoffier (upstream Git author) |
| `test/java/com/pembana/search/PembanaSearchCompatibilityTest.java` | Thibault F; Michael Mollard; Luc Boussant-roux; HamidReza Ireh; Diego Corredor; fernandorubio (upstream Git author); vincentescoffier (upstream Git author); Mahdi Lazraq; Vincent Escoffier |
| `test/java/com/pembana/search/PembanaSearchTestApplication.java` | Thibault F; Michael Mollard |
| `test/java/com/pembana/search/UserType.java` | HamidReza Ireh |
| `test/java/com/pembana/search/Users.java` | Michael Mollard; HamidReza Ireh; Diego Corredor; fernandorubio (upstream Git author); Mahdi Lazraq; vincentescoffier (upstream Git author); Vincent Escoffier |
| `test/java/com/pembana/search/UsersRepository.java` | Thibault F; Michael Mollard |

## Reproduce the evidence

```sh
git log --follow --no-merges --format='%H %an %as %s' \
  c4b404dc6cf22b540d3e7f6211e557f5d83b6c61 -- <upstream-source-path>

git show -s --format='%B' <commit>  # inspect explicit Co-authored-by trailers

git blame -w -M -C -C --line-porcelain -L 32,36 \
  c4b404dc6cf22b540d3e7f6211e557f5d83b6c61 -- \
  src/main/kotlin/com/sipios/springsearch/SpecificationsBuilder.kt
```

## Verification and limits

This change edits class Javadoc and documentation only. A source-token comparison
confirms that executable Java is unchanged. Existing Apache/MIT header blocks,
root `LICENSE`, `LICENSE.txt`, `NOTICE`, and historical evidence are byte-identical.
The Java-only adaptation helper's only change is removal of its collective
author tag; its fork author and license notices remain.

With build JDK 21, the full default H2 verification passes all **148 tests**, with
**zero Checkstyle violations**, strict Javadoc generation, and unchanged coverage
gates. Dependency-Check and Maven artifact signing are skipped for this local
compatibility build; Git commit signing is separate. No database/configuration,
framework/dependency, API, query, or CI behavior changes are introduced.

Git author fields and co-author trailers are contributor records, not independent
proof of legal ownership or an exhaustive reconstruction of authorship predating
this repository. Unresolved identities stay explicit so a maintainer can improve
attribution later using stronger evidence. No email-derived names, blanket named
authorship claims, rewritten upstream history, release tags, or publication are added.
