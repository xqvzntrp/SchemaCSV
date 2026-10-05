# SchemaCSV

**SchemaCSV** is a lightweight relational data engine for CSV files. It adds explicit schema, primary-key, foreign-key, composite-key, and required-value validation while keeping plain CSV files as the durable source of truth.

> Phase 1 goal: prove that CSV files can behave like a small relational dataset with enforceable schema, primary-key, and foreign-key rules.

## Status

SchemaCSV v0.1.0 implements **Phase 1 — Relational Validation**.

- Plain Java 21; no runtime dependencies
- CSV-backed tables
- CSV-backed schema definitions
- Required columns and values
- Primary keys, including composite keys
- Foreign keys, including composite keys
- File and row diagnostics
- `validate` CLI command
- Valid and intentionally invalid example datasets
- Dependency-free test runner

## Quick start

Requires Java 21 or newer.

```bash
./scripts/test.sh
./scripts/schemacsv validate examples/basic
```

Expected validation output:

```text
SchemaCSV 0.1.0
tables=4 rows=5
validation=OK
```

An invalid relationship produces a non-zero exit status and a diagnostic similar to:

```text
field.csv:2: FOREIGN_KEY_MISSING_PARENT: Foreign key 'fk_field_category' (WAYFAIR, SECTIONAL) references category(channel_code, code) but no matching parent row exists
```

## Dataset layout

```text
my-dataset/
├── schema/
│   ├── tables.csv
│   ├── columns.csv
│   ├── primary_keys.csv
│   └── foreign_keys.csv
└── data/
    ├── channel.csv
    ├── category.csv
    └── ...
```

### `tables.csv`

```csv
table_name,file
channel,channel.csv
category,category.csv
```

### `columns.csv`

```csv
table_name,column_name,required,position
category,channel_code,true,1
category,code,true,2
category,name,true,3
```

### `primary_keys.csv`

Rows sharing a `key_name` form one key. `position` defines composite-key order.

```csv
table_name,key_name,column_name,position
category,pk_category,channel_code,1
category,pk_category,code,2
```

### `foreign_keys.csv`

Rows sharing a `key_name` form one relationship. This example defines a composite foreign key from `field` to `category`.

```csv
table_name,key_name,column_name,position,referenced_table,referenced_column
field,fk_field_category,channel_code,1,category,channel_code
field,fk_field_category,category_code,2,category,code
```

## CLI

```bash
./scripts/schemacsv validate <dataset-directory>
```

Exit codes:

- `0` — dataset is valid
- `1` — relational constraint violations were found
- `2` — usage, schema, or loading error

## Design principles

1. **CSV remains the source of truth.** SchemaCSV does not hide data in a binary database file.
2. **Relationships are explicit.** Repeated strings become checked keys rather than informal conventions.
3. **Validation is deterministic.** The same files should produce the same result locally and in CI.
4. **Diagnostics should point to the edit.** Constraint failures include the source file and row whenever possible.
5. **The core stays generic.** SchemaCSV does not know about channels, products, or any other particular business domain.

## Roadmap

The roadmap describes the intended direction of the project and may change as the design evolves.

### Phase 1 — Relational Validation ✅

- Standalone Java project
- CSV-backed tables and schemas
- Primary and foreign key constraints
- Composite keys
- Required-value validation
- Validation CLI
- File and row diagnostics

### Phase 2 — Safe Editing and Persistence

- Insert, update, and delete operations
- Primary-key changes
- Foreign-key propagation
- Restrict and cascade rules
- Transaction-style editing
- Atomic CSV writes
- Reload-after-save validation

### Phase 3 — Query and Navigation

- Table inspection
- Filtering and lookup
- Relationship traversal
- `references` / `referenced-by` navigation
- Sorting and projection
- Richer CLI exploration commands

### Phase 4 — Interactive Editor

- Interactive terminal UI
- Table and record browsing
- Record editing
- Relationship-aware selectors
- Immediate validation feedback
- Safe save and rollback workflow

### Phase 5 — Schema Evolution

- Schema migrations
- Rename columns and tables
- Key migration support
- Default values
- Additional constraints
- Dataset version metadata

### Future possibilities

- Desktop or web UI
- Import/export adapters
- Stable Java library API
- Pluggable storage formats
- Indexes for larger datasets

## Building without Maven

```bash
./scripts/build.sh
java -jar out/schemacsv-0.1.0.jar validate examples/basic
```

A `pom.xml` is included for Maven-based environments as well.

## License

MIT
