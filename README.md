# Pembana Search

Advanced search expressions for Spring Data JPA, with optional Spring MVC query
binding, field restrictions, input limits, and structured validation errors.

**Development version:** `1.0.0-SNAPSHOT`. No artifact has been published.
Build/install the reactor locally, then choose the integration you need:

| Artifact (`com.pembana`) | Responsibility |
| --- | --- |
| `pembana-search-core` | Immutable query model and expression parser; no Spring or persistence dependencies |
| `pembana-search-data-jpa` | Model-to-specification translation and retained Java JPA entry points |
| `pembana-search-spring-boot-starter` | Optional Boot/MVC binding with an overridable resolver |

`pembana-search` is now the parent/aggregator POM. The API is evolving toward 1.0.

## Compatibility

| Component | Verified baseline |
| --- | --- |
| Spring Boot | 4.1.1 |
| Build JDK | 21 (Maven, Checkstyle, compiler, and Javadoc) |
| Consumer/test runtimes | 17, 21, 25; compiled with `--release 17` |
| H2 | 2.4.240; default test database, no Docker required |
| PostgreSQL | 18.1 through Testcontainers 2.0.5; Docker required for that profile |

Boot 3 and other framework/database combinations are not currently advertised
as supported. Production and tests use Java; Kotlin tooling is not required.

## Build locally

Select OpenJDK 21.0.8 or newer in the Java 21 line in `JAVA_HOME` and `PATH`.
The compiler emits Java 17-compatible code; the build tools themselves require
Java 21. See the [nullability checks](docs/development.md#nullability-checks)
for compiler requirements and annotation conventions.

```sh
# H2 by default; artifact signing and the network vulnerability scan are skipped.
./mvnw -Dgpg.skip=true -Ddependency-check.skip=true clean install

# Optional PostgreSQL verification; start a Docker-compatible engine first.
./mvnw -Dgpg.skip=true -Ddependency-check.skip=true -Dspring.profiles.active=postgresql clean verify
```

The wrapper supplies Maven 3.10.0. Tests and coverage remain enabled in these
commands. Git commit signing is separate from Maven artifact signing.

## Add to a Spring Boot application

After installing this development snapshot locally:

```xml
<dependency>
    <groupId>com.pembana</groupId>
    <artifactId>pembana-search-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

The starter brings Boot JPA and MVC integration. Your application supplies its
entities, database driver, configuration and security policy. For direct JPA
without Boot/MVC binding, depend on `pembana-search-data-jpa` instead and supply
a JPA provider. Extend `JpaSpecificationExecutor` on your repository:

```java
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
}
```

Bind an advanced query with the correctly spelled `annotation` package:

```java
import com.pembana.search.annotation.SearchSpec;
import org.springframework.data.jpa.domain.Specification;

@GetMapping("/users")
public List<User> findUsers(@SearchSpec Specification<User> search) {
    return search == null ? repository.findAll() : repository.findAll(search);
}
```

For example, the `search` parameter can contain
`name:Ada* AND active:true`. URL-encode expressions when sending HTTP requests.
Boot discovers the resolver automatically; scanning the library package is not
required. Consumer authorization/tenant restrictions must be composed outside
user-controlled Boolean expressions.

Default matching preserves the upstream advanced-expression behavior. Opt in to
stricter validation explicitly:

```java
@SearchSpec(strictValidation = true,
            allowedFields = {"name", "active"},
            maxQueryLength = 4096,
            maxNestingDepth = 16)
Specification<User> search
```

The [query reference](docs/reference/query-language.md) documents important legacy
limits, including remaining case-operator asymmetry, SQL LIKE metacharacters, and implicit
association joins. Strict validation does not silently change matching semantics.

For case-insensitive equality and patterns, set `@SearchSpec(caseSensitiveFlag = false)`.
For example, `name:ADA` then matches `Ada`, and `name!ADA` excludes it. The default
remains `true`; membership still has separate case behavior. See
[case sensitivity](docs/reference/case-sensitivity.md) for the recommended form
default, null/collation limits and the proposed per-property override design.

## Core and programmatic queries

```java
import com.pembana.search.core.Comparison;
import com.pembana.search.core.ComparisonOperator;
import com.pembana.search.core.ExpressionParser;
import com.pembana.search.core.Query;

Query parsed = new ExpressionParser().parse("name:Ada* AND active:true");
Query built = new Comparison("name", ComparisonOperator.STARTS_WITH, "Ada")
        .and(new Comparison("active", ComparisonOperator.EQUALS, "true"));
// parsed.equals(built) is true; neither path needs Spring or a database.
```

With the JPA module, pass the same tree to
`new JpaQueryTranslator<User>(settings).translate(built)` or
`new SpecificationsBuilder<User>(settings).withQuery(built).build()`.
Programmatic values remain data; they are never concatenated into expressions.
See [architecture](docs/architecture.md) and [module verification](docs/modules/README.md).

## Documentation

- [Getting started and direct JPA usage](docs/getting-started.md)
- [Query language, matching, validation, and errors](docs/reference/query-language.md)
- [Configuration and public entry points](docs/reference/configuration.md)
- [Architecture and dependency boundaries](docs/architecture.md)
- [Migration from Spring Search and earlier imported snapshots](docs/migration.md)
- [Building, testing, NVD configuration, and troubleshooting](docs/development.md)
- [Spring Checkstyle configuration and verification](docs/checkstyle/README.md)
- [Release/versioning checklist](docs/releasing.md)
- [Contributing](CONTRIBUTING.md), [changelog](CHANGELOG.md), [documentation index](docs/README.md)

`./mvnw -Dgpg.skip=true -Ddependency-check.skip=true package` produces binary,
source, and Javadoc JARs. Generated API documentation is in
each module’s `target/reports/apidocs/` directory.

## License and provenance

Pembana Search contributions and distribution use [Apache License 2.0](LICENSE).
Inherited Spring Search material retains its [MIT license](LICENSE.txt) and
[NOTICE](NOTICE). This fork derives from
[theodo-fintech/spring-search](https://github.com/theodo-fintech/spring-search)
revision `c4b404dc6cf22b540d3e7f6211e557f5d83b6c61`.
Historical evidence and archived upstream documentation retain their original
names and coordinates; they are not current installation instructions.
