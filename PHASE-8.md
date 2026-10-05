# Phase 8 — Indexing and Performance

SchemaCSV v0.10.0 adds schema-derived, in-memory hash indexes so relational integrity checks and common query/navigation operations do not need to scan entire CSV tables repeatedly.

## Goals

- Keep CSV files as the only durable source of truth.
- Build indexes automatically from existing PK, UNIQUE, and FK metadata.
- Accelerate exact-match lookups and relationship traversal without changing query semantics.
- Reuse indexes during PK/UNIQUE/FK validation.
- Make index selection observable from the CLI.
- Avoid persistent index files or cache invalidation complexity in this phase.

## Index model

Indexes are rebuilt when a dataset is loaded. SchemaCSV creates physical hash indexes for:

- primary-key column tuples,
- unique-key column tuples,
- foreign-key child column tuples,
- referenced parent column tuples when they are not already covered by an equivalent PK/UNIQUE index.

Equivalent column tuples are deduplicated, so a referenced PK is not indexed twice just because it is also an FK target.

Indexes store `Key -> List<CsvRecord>`. Primary and unique indexes normally contain one row per key, while FK child indexes naturally support one-to-many relationships.

## Query planning

Exact-match filters use the most specific available index whose full column tuple is present in the filter. Any remaining predicates are evaluated against the reduced candidate set.

If no suitable index exists, SchemaCSV falls back to a table scan.

```bash
./scripts/schemacsv explain examples/basic field field_key=1000
# INDEX table=field name=pk_field columns=field_key

./scripts/schemacsv explain examples/basic field category_key=100
# INDEX table=field name=fk_field_category_child columns=category_key

./scripts/schemacsv explain examples/basic field name=Brand
# SCAN table=field
```

## Index inspection

```bash
./scripts/schemacsv indexes examples/basic
```

The command reports table, index name, kind, indexed columns, distinct key count, and indexed row count.

## Validation

Primary-key, unique-key, and foreign-key validation now share the same schema-derived index layer used by queries. Validation behavior and diagnostics remain unchanged.

## Design boundary

Phase 8 does **not** add persisted indexes, B-trees, range queries, or a cost-based optimizer. Indexes are intentionally ephemeral and deterministic: the CSV data plus schema remain sufficient to reconstruct all runtime acceleration structures.
