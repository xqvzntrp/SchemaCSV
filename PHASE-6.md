# Phase 6 — Schema Evolution

SchemaCSV v0.8.0 introduces validated, staged schema migrations while keeping CSV files as the durable source of truth.

## Included

- `schema/metadata.csv` with `schema_version` and `dataset_version`
- `version` CLI command
- safe table renames
- safe column renames
- key/FK/unique metadata propagation during column/table renames
- add-column migrations with optional required/default behavior
- add-unique migrations
- staged reload + full constraint validation before live replacement
- atomic replacement of the schema and data directories where supported

## Commands

```bash
./scripts/schemacsv version examples/basic

./scripts/schemacsv migrate examples/basic rename-table field attribute
./scripts/schemacsv migrate examples/basic rename-column field field_key field_id
./scripts/schemacsv migrate examples/basic add-column category status --required --default ACTIVE
./scripts/schemacsv migrate examples/basic add-unique field uq_field_name category_key,name
```

Each successful migration advances both metadata versions. A migration is rejected before live replacement if the staged dataset cannot be reloaded or violates PK, FK, required-value, or unique constraints.

## Deliberately deferred

Phase 6 does not attempt arbitrary type conversion, destructive column removal, or a general migration DSL. Those can be added later once migration semantics are stable.
