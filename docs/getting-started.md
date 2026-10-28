# Getting started

Pembana Search translates advanced expressions to Spring Data JPA Specifications.
It is generic: consumers supply their entities, repositories, database, and
mandatory authorization restrictions. There is no dependency on the Pembana
application's model.

## Build and consume the snapshot

No artifact is published yet. Select Java 17, 21, or 25 and install from this
repository using its Maven wrapper:

```sh
./mvnw -Dgpg.skip=true -Ddependency-check.skip=true clean install
```

Add `com.pembana:pembana-search:1.0.0-SNAPSHOT` to your application's dependencies.
The application's Boot 4.1.1 dependency management should align Spring/Hibernate
versions. It must supply `spring-boot-starter-data-jpa`, a database driver, and,
for request binding, `spring-boot-starter-webmvc`. The library's framework
starters are provided dependencies; it does not select a production database.
See the complete dependency snippet in the [README](../README.md).

## HTTP search

Use your entity's Java attribute names, not SQL column names. For an application
entity `User` with `name` and `active` attributes:

```java
import com.pembana.search.annotation.SearchSpec;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> { }

/**
 * User search endpoint; application authorization is supplied separately.
 * @author Gunnar Hillert
 */
@RestController
class UserController {
    private final UserRepository repository;

    /**
     * Creates the endpoint with its application repository.
     * @param repository application repository
     */
    UserController(UserRepository repository) { this.repository = repository; }

    /**
     * Executes the supplied optional search.
     * @param search resolved search, or null when absent or empty
     * @return matching users
     */
    @GetMapping("/users")
    List<User> users(@SearchSpec(searchParam = "filter", strictValidation = true,
            allowedFields = {"name", "active"}, maxQueryLength = 4096, maxNestingDepth = 16)
            Specification<User> search) {
        return search == null ? repository.findAll() : repository.findAll(search);
    }
}
```

A request might be:

```sh
curl --get --data-urlencode 'filter=name:Ada* AND active:true' http://localhost:8080/users
```

Boot discovers the MVC resolver from the library's auto-configuration imports;
no `@ComponentScan` of library packages is needed. `@RepositoryRestResource` is
not required. The example assumes `User` is an application-supplied JPA entity.
The endpoint is illustrative; configure authentication and caller restrictions
as appropriate to the application.

## Direct JPA usage without MVC

`SpecificationsBuilder` needs a `SearchSpec` policy. A normal Java annotation can
be read from a policy method; no fabricated annotation implementation is needed:

```java
import com.pembana.search.SpecificationsBuilder;
import com.pembana.search.annotation.SearchSpec;
import org.springframework.data.jpa.domain.Specification;

/**
 * Direct JPA search policy and expression adapter.
 * @author Gunnar Hillert
 */
public final class UserSearch {
    /**
     * Defines policy without an HTTP endpoint.
     * @param unused annotation target
     */
    private static void policy(@SearchSpec(strictValidation = true,
            allowedFields = {"name", "active"}) Specification<User> unused) { }

    /**
     * Parses an expression without executing it.
     * @param expression advanced expression
     * @return JPA specification
     * @throws ReflectiveOperationException if the policy declaration is absent
     */
    public static Specification<User> parse(String expression) throws ReflectiveOperationException {
        SearchSpec options = UserSearch.class.getDeclaredMethod("policy", Specification.class)
                .getParameters()[0].getAnnotation(SearchSpec.class);
        return new SpecificationsBuilder<User>(options).withSearch(expression).build();
    }
}
```

Pass the result to `repository.findAll`, `count`, or the sorted/paged overload.
Parsing requires no running Spring context/database, but this artifact's API
still depends on Spring Data JPA and Spring web error types. It is not yet a
Spring-independent core or a programmatic immutable query DSL. JPA field/type
validation occurs when the predicate is constructed, not during parsing alone.
A builder without `withSearch` supplies a no-predicate specification; repeated
`withSearch` calls replace its expression.

## Mandatory consumer scopes

Compose mandatory restrictions outside the entire user expression:

```java
Specification<User> effective = search == null ? tenantScope : tenantScope.and(search);
return repository.findAll(effective, pageable);
```

Keep `tenantScope` application-owned. Do not concatenate a tenant expression
with untrusted query text. Supply explicit sorting for stable pagination. Search
field restrictions are not a replacement for row-level authorization.

Read the [query reference](reference/query-language.md) before exposing advanced
expressions, particularly its matching and association limitations. The
[configuration reference](reference/configuration.md) covers error rendering.
