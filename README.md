# SchemaCSV

**SchemaCSV** is a lightweight relational data engine for CSV files. It adds explicit schema, relational constraints, and safe editing while keeping plain CSV files as the durable source of truth.

> SchemaCSV keeps CSV files human-readable and Git-friendly while adding database-style integrity.

## Status

SchemaCSV v0.4.0 implements **Phase 3 — Relational Identity Refinement** on top of the Phase 1 validator and Phase 2 editing/persistence engine.

- Plain Java 21; no runtime dependencies
- CSV-backed tables and schema definitions
- Required columns and values
- Primary keys, including composite keys
- Foreign keys, including composite keys
- Unique keys, including composite/scoped uniqueness
- Stable `*_key` relational identity
- Editable `*_code` business naming
- Transaction-style insert, update, and delete operations
- Recursive foreign-key propagation when true referenced keys change
- `RESTRICT` and `CASCADE` delete rules
- Atomic replacement of the `data/` directory
- Reload-after-save validation
- File and row diagnostics

## Quick start

Requires Java 21 or newer.

```bash
./scripts/test.sh
./scripts/schemacsv validate examples/basic
```

Expected output:

```text
SchemaCSV 0.4.0
tables=4 rows=5
validation=OK
```

The included invalid example intentionally points a field at a missing category key and produces a non-zero exit status with a file/row diagnostic.

## Relational identity and business codes

SchemaCSV deliberately separates **relational identity** from **business-aware naming**.

- `*_key` columns are intended for primary-key and foreign-key relationships. Keys should be stable, compact, and optimized for integrity.
- `*_code` columns are intended for human-readable or business-facing identifiers. Codes should be recognizable, editable, and optimized for authoring.

Example:

```csv
category_key,channel_key,category_code,name
100,1,SECTIONALS_306,Sectionals
```

```csv
field_key,category_key,field_code,name
1000,100,core::supplier_part_number,Supplier Part Number
```

The relationship is:

```text
field.category_key -> category.category_key
```

while editors work with values such as:

```text
SECTIONALS_306
core::supplier_part_number
```

A code rename therefore does not rewrite dependent foreign keys:

```text
category_key = 100
category_code = SECTIONALS_306 -> SECTIONALS_307
```

`category_key` remains `100`, so every field referencing the category remains valid without a cascade.

If a true key is changed, SchemaCSV still propagates that change through dependent foreign keys transactionally.

## Dataset layout

```text
my-dataset/
├── schema/
│   ├── tables.csv
│   ├── columns.csv
│   ├── primary_keys.csv
│   ├── unique_keys.csv
│   └── foreign_keys.csv
└── data/
    ├── channel.csv
    ├── category.csv
    ├── field.csv
    └── value.csv
```

### `tables.csv`

```csv
table_name,file
channel,channel.csv
category,category.csv
field,field.csv
```

### `columns.csv`

```csv
table_name,column_name,required,position
category,category_key,true,1
category,channel_key,true,2
category,category_code,true,3
category,name,true,4
```

### `primary_keys.csv`

Rows sharing a `key_name` form one key. `position` defines composite-key order.

```csv
table_name,key_name,column_name,position
channel,pk_channel,channel_key,1
category,pk_category,category_key,1
field,pk_field,field_key,1
```

### `unique_keys.csv`

Unique keys protect business identifiers without turning them into relational identity. Composite unique keys provide scoped uniqueness.

```csv
table_name,key_name,column_name,position
channel,uk_channel_code,channel_code,1
category,uk_category_code,channel_key,1
category,uk_category_code,category_code,2
field,uk_field_code,category_key,1
field,uk_field_code,field_code,2
```

This means two different channels may reuse the same `category_code`, but one channel cannot contain the same code twice.

### `foreign_keys.csv`

Foreign keys normally point to stable key columns:

```csv
table_name,key_name,column_name,position,referenced_table,referenced_column
category,fk_category_channel,channel_key,1,channel,channel_key
field,fk_field_category,category_key,1,category,category_key
value,fk_value_field,field_key,1,field,field_key
```

## Constraint pattern

The recommended pattern is:

```text
PRIMARY KEY channel(channel_key)
UNIQUE channel(channel_code)

PRIMARY KEY category(category_key)
FOREIGN KEY category(channel_key) REFERENCES channel(channel_key)
UNIQUE category(channel_key, category_code)

PRIMARY KEY field(field_key)
FOREIGN KEY field(category_key) REFERENCES category(category_key)
UNIQUE field(category_key, field_code)
```

SchemaCSV does not require column names to end in `_key` or `_code`; those names are a documented design convention. Integrity comes from the schema files themselves.

## CLI

Validate:

```bash
./scripts/schemacsv validate <dataset-directory>
```

Insert a category using a stable key and editable code:

```bash
./scripts/schemacsv insert examples/basic category \
  category_key=101 channel_key=1 category_code=SOFAS_101 name=Sofas
```

Rename only the business code:

```bash
./scripts/schemacsv update examples/basic category \
  --key category_key=100 \
  --set category_code=SECTIONALS_307
```

No foreign-key cascade is needed because `category_key=100` did not change.

Changing a true relational key remains supported and propagates through dependent foreign keys:

```bash
./scripts/schemacsv update examples/basic category \
  --key category_key=100 \
  --set category_key=101
```

Delete is restrictive by default:

```bash
./scripts/schemacsv delete examples/basic category category_key=100
```

Explicit cascading delete:

```bash
./scripts/schemacsv delete examples/basic category --cascade category_key=100
```

Exit codes:

- `0` — command succeeded / dataset is valid
- `1` — relational constraint violations were found by `validate`
- `2` — usage, schema, loading, or editing error

## Design principles

1. **CSV remains the source of truth.** SchemaCSV does not hide data in a binary database file.
2. **Relationships are explicit.** Referential integrity comes from declared keys, not repeated strings and convention alone.
3. **Keys and codes serve different purposes.** `_key` is optimized for integrity; `_code` is optimized for human/business editing.
4. **Business uniqueness is explicit.** Unique constraints preserve code semantics without making codes foreign keys.
5. **Validation is deterministic.** The same files should produce the same result locally and in CI.
6. **Diagnostics should point to the edit.** Constraint failures include source file and row whenever possible.
7. **Writes are validated before replacement.** Staged CSV output is reloaded and validated before becoming live data.
8. **The core stays generic.** SchemaCSV does not know about channels, products, or any particular business domain.

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

### Phase 2 — Safe Editing and Persistence ✅

- Insert, update, and delete operations
- Key changes with recursive foreign-key propagation
- Restrict and cascade delete rules
- Transaction-style editing
- Atomic data-directory replacement
- Reload-after-save validation
- Editing CLI commands

### Phase 3 — Relational Identity Refinement ✅

- Separate stable `*_key` identity from editable `*_code` naming
- Add unique constraints for business-code scopes
- Prefer key-based foreign-key relationships in examples and workflows
- Keep code renames independent from relational-key changes
- Preserve cascading behavior for true key changes

### Phase 4 — Query and Navigation

- Table inspection
- Filtering and lookup
- Relationship traversal
- `references` / `referenced-by` navigation
- Sorting and projection
- Richer CLI exploration commands

### Phase 5 — Interactive Editor

- Interactive terminal UI
- Table and record browsing
- Record editing
- Relationship-aware selectors
- Immediate validation feedback
- Safe save and rollback workflow

### Phase 6 — Schema Evolution

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

## Building

### Maven

Requires Java 21 or newer. The POM pins a modern Maven compiler plugin and compiles with `--release 21`.

```bash
mvn clean package
java -jar target/schemacsv-0.4.0.jar validate examples/basic
```

### Without Maven

```bash
./scripts/build.sh
java -jar out/schemacsv-0.4.0.jar validate examples/basic
```

## License

MIT
