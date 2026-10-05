# SchemaCSV v0.5.0 — Maven Test Integration Release

SchemaCSV v0.5.0 is a maintenance release on top of Phase 3. It does not implement Phase 4 query/navigation features.

## Purpose

v0.4.0 fixed Java 21 compilation under Maven, but Maven Surefire reported `Tests run: 0` because the existing suite was a standalone Java test runner rather than a JUnit test suite.

v0.5.0 makes those behavioral tests first-class Maven tests while preserving the dependency-free runner.

## Changes

- Adds JUnit Jupiter 5 for Maven test execution.
- Pins Maven Surefire 3.5.2.
- Exposes the existing 10 behavioral checks as JUnit 5 tests.
- Shares one `TestCases` implementation between JUnit and the standalone `TestRunner`.
- Preserves `./scripts/test.sh` for environments that want a dependency-free test path.
- Bumps the CLI and artifact version to 0.5.0.

## Acceptance

Maven:

```bash
mvn test
```

Expected summary:

```text
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Dependency-free path:

```bash
./scripts/test.sh
```

Expected summary:

```text
TESTS: 10 passed, 0 failed, 10 total
```
