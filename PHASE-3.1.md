# SchemaCSV v0.4.0 — Build Compatibility Release

SchemaCSV v0.4.0 is a maintenance release on top of Phase 3. It does not implement Phase 4 query/navigation features.

## Changes

- Pins `maven-compiler-plugin` 3.13.0.
- Compiles explicitly with Java 21 via `<release>21</release>`.
- Updates Maven, launcher, and manual build artifacts to version 0.4.0.
- Keeps the Phase 3 relational identity model unchanged.

## Acceptance criteria

```bash
mvn clean package
java -jar target/schemacsv-0.4.0.jar validate examples/basic
./scripts/build.sh
./scripts/schemacsv validate examples/basic
```

The Maven build and the dependency-free `javac` build must both succeed.
