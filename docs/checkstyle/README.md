# Spring JavaFormat Checkstyle and build JDK

Maven uses `spring-javaformat-checkstyle` **0.0.48** with Checkstyle **14.1.0**
and Maven Checkstyle Plugin **3.6.0**. These are plugin dependencies, not library
runtime dependencies. The `check` goal is bound to `validate`, so local builds
and GitHub Actions fail on style violations before compilation.

## Build on Java 21; target and test Java 17

Run Maven with JDK 21 in `JAVA_HOME` and `PATH`. Checkstyle 14 requires Java 21;
this does not increase the library's minimum consumer runtime. The POM retains
`java.version=17` and explicitly sets `maven.compiler.release` to that value.
Compilation therefore checks Java 17 APIs and emits Java 17 class files.

```sh
./mvnw validate
./mvnw -Dgpg.skip=true -Ddependency-check.skip=true clean verify

# Maven/Checkstyle/compiler stay on JDK 21; Surefire forks the selected runtime.
./mvnw -Dgpg.skip=true -Ddependency-check.skip=true -Djvm=/path/to/jdk17/bin/java clean verify
```

CI installs the selected runtime, saves its path, then selects build JDK 21.
The existing six jobs run tests on Java 17/21/25 against H2 and PostgreSQL. Each
packaged-JAR smoke check uses the same selected runtime as Surefire. Default H2
still requires no Docker; PostgreSQL remains an explicit Testcontainers profile.

## Style policy

`src/checkstyle/checkstyle.xml` applies Spring checks to handwritten main and
test Java, with `com.pembana.search` as the project import group. Generated ANTLR
sources and non-Java resources are excluded; production/test Java is not broadly
suppressed. Five package-info files document the handwritten production packages.

The configuration deliberately preserves these existing contracts:

- The project-specific Apache 2 header is checked through Spring's header check,
  with the original 2026 copyright and retained MIT attribution block unchanged.
- The singular `Test.java` corpus identities remain; Spring's plural test-file
  naming convention is disabled rather than renaming the verified scenario classes.
- Only the "Please use AssertJ imports" rule is suppressed under test sources.
  Existing JUnit assertions remain intact, and other regular-expression checks remain active.
- Static imports from the existing test annotation/set factories are explicitly allowed.
- The Spring ternary check still requires parentheses around complex conditions,
  but allows equality conditions. Existing null-branch ordering is retained.

Formatting cleanup uses tabs, Spring import grouping, explicit imports and
instance qualifiers, parenthesized lambda arguments/ternary conditions, and
expression lambdas in eleven existing exception assertions. Test assertion calls,
values and method identities are unchanged. The test-only nested entity is moved
after its enclosing methods without changing its declaration or behavior.

```sh
./mvnw io.spring.javaformat:spring-javaformat-maven-plugin:0.0.48:apply
./mvnw validate
```

Formatting is an explicit developer command, not an automatic source rewrite
on every build. Review the diff; imports, qualifications and other style issues
may need separate cleanup. No custom formatting scripts or Python setup are required.

## Attribution

Converted files retain their Apache header and the separate "Derived from Spring
Search" MIT attribution. Their class Javadoc now credits verified individual
upstream contributors alongside `Gunnar Hillert` for the Java port/fork work;
the collective tag used during the style milestone is removed where names are
established. See the [per-file provenance](../provenance/upstream-authors.md).
The Java-only adaptation helper credits its fork author. The project-wide Woody
Rousseau copyright notice is not used as a universal author tag, and recorded
handles are not replaced with guessed full names or "Original Author" placeholders.

## Verification

All six JDK-21-build/runtime/database combinations pass all **148 tests**, including
all **113 original scenarios**, with zero failures/errors/skips and **zero
Checkstyle violations**. Each packaged Java caller passes on its selected runtime.
Coverage and strict Javadoc gates pass. Compiled production/test classes have
major version **61** (Java 17).

An AST audit compares every original Java file's literal values and assertion
calls against revision `20613ef1ac048f6d46066d1a0fa9dff6cad48fa3`; all 43 files
match. Disassembled instruction listings for all 64 production classes match the
preceding identity baseline built with the same JDK 21, confirming the formatting
and explicit instance qualifiers do not change production instructions. Debug
line metadata and source formatting can differ.

[results.json](results.json) records runtime identities from Surefire, build
versions, counts, style results, bytecode targeting, dependency comparisons and
evidence hashes. Native logs, reports and audit output remain under ignored
`build/checkstyle/`. Checkstyle XML is retained in CI artifacts, including failed
runs. CI is configured but has not run on GitHub for this local change.
Maven artifact signing and the network vulnerability scan are skipped by these
compatibility commands; Git commit signing remains separate. Nothing is published.
