# Phase 4 — Query and Navigation

SchemaCSV v0.6.0 adds read-only exploration on top of the validated relational dataset.

## Scope

- Inspect available tables, files, row counts, and primary keys.
- Select rows with exact-match filters.
- Project selected columns.
- Sort by one or more columns.
- Lookup a single row by key or business-aware criteria.
- Traverse declared foreign keys from a row to its parent rows.
- Traverse incoming foreign keys to rows that reference a selected row.
- Expose all of the above through the CLI without introducing SQL parsing.

## CLI

```bash
./scripts/schemacsv tables examples/basic
./scripts/schemacsv select examples/basic field --where category_key=100 --columns field_code,name --sort field_code
./scripts/schemacsv lookup examples/basic category category_code=SECTIONALS_306
./scripts/schemacsv references examples/basic value value_key=10000
./scripts/schemacsv referenced-by examples/basic category category_key=100
```

`select` uses exact string equality in v0.6.0. Multiple `--where` pairs are ANDed together. Sort order is ascending and deterministic.

## Acceptance criteria

- Existing Phase 1–3 behavior remains valid.
- Shared test suite increases from 10 to 15 tests.
- Both the dependency-free runner and Maven/JUnit surface exercise the same cases.
- Query/navigation operations are read-only and never modify source CSV files.
- Relationship traversal is derived from schema foreign-key declarations.
