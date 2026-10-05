# Phase 7 — Defaults, Check Constraints, and Migration Audit

SchemaCSV v0.9.0 extends schema evolution with durable authoring rules and migration traceability.

## Goals

- Make column defaults persistent schema metadata rather than migration-only backfills.
- Apply defaults automatically when inserts omit configured columns.
- Add small, deterministic check constraints that remain CSV-native and easy to review.
- Record every successful schema migration in an append-only audit file.
- Preserve defaults and checks through table/column renames.

## New schema files

### `defaults.csv`

```csv
table_name,column_name,default_value
category,status,ACTIVE
```

### `check_constraints.csv`

```csv
table_name,constraint_name,column_name,operator,operand
category,ck_status,status,IN,ACTIVE|INACTIVE
category,ck_code,category_code,MATCHES,[A-Z0-9_]+
```

Supported operators:

- `IN`
- `MATCHES`
- `MIN_LENGTH`
- `MAX_LENGTH`
- `NUMERIC_MIN`
- `NUMERIC_MAX`

### `migrations.csv`

```csv
migration_id,schema_version,dataset_version,operation,detail,timestamp_utc
```

A history row is written only after the migration has been staged. If validation fails, the staged files and staged history row are discarded.

## CLI

```bash
./scripts/schemacsv migrate <dir> set-default <table> <column> <value>
./scripts/schemacsv migrate <dir> add-check <table> <name> <column> <operator> <operand>
./scripts/schemacsv history <dir>
```

`add-column --default` also persists the default in `defaults.csv` so future inserts receive it automatically.

## Integrity behavior

Defaults are applied before transaction validation. Check constraints run as part of the normal `ConstraintValidator`, so they are enforced by validation, CLI edits, staged interactive edits, transaction commits, and schema migrations.

Table and column renames update default/check metadata along with PK, FK, UNIQUE, and data-file references.
