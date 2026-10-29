# Pembana Search

Advanced search expressions for Spring Data JPA, with optional Spring MVC query
binding, field restrictions, input limits, and structured validation errors.

**Development snapshot:** `com.pembana:pembana-search:1.0.0-SNAPSHOT`.
No Pembana Search artifact has been published. Build and install locally before
using the dependency below. The API is evolving toward 1.0; this single artifact
currently includes parsing, JPA integration, and MVC binding.

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

Select JDK 21 in `JAVA_HOME` and `PATH`. The compiler emits Java 17-compatible
code; the build tools themselves require Java 21.

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
    <artifactId>pembana-search</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Your application supplies Spring Data JPA, a JDBC driver, and, for HTTP binding,
Spring MVC. For Boot 4 use `spring-boot-starter-data-jpa` and
`spring-boot-starter-webmvc`. Extend `JpaSpecificationExecutor` on your repository:

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
limits, including case-operator asymmetry, SQL LIKE metacharacters, and implicit
association joins. Strict validation does not silently change matching semantics.

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
`target/reports/apidocs/` with the current Maven Javadoc plugin.

## License and provenance

Pembana Search contributions and distribution use [Apache License 2.0](LICENSE).
Inherited Spring Search material retains its [MIT license](LICENSE.txt) and
[NOTICE](NOTICE). This fork derives from
[theodo-fintech/spring-search](https://github.com/theodo-fintech/spring-search)
revision `c4b404dc6cf22b540d3e7f6211e557f5d83b6c61`.
Historical evidence and archived upstream documentation retain their original
names and coordinates; they are not current installation instructions.
