# Phase 3 — Relational Identity Refinement

SchemaCSV v0.3.0 separates durable relational identity from editable business naming.

## Completed

- Added `unique_keys.csv` schema support.
- Added single-column and composite unique-key validation.
- Converted the examples to stable surrogate-style `*_key` primary keys.
- Converted foreign keys to reference stable key columns.
- Added scoped `*_code` uniqueness for business-facing identifiers.
- Verified that editing a business code does not rewrite foreign keys.
- Preserved recursive foreign-key propagation when a true referenced key changes.
- Expanded the test suite around key/code semantics and uniqueness.

## Design rule

Use keys for integrity and codes for meaning:

```text
*_key  -> stable relational identity
*_code -> editable business-aware identifier
```

SchemaCSV enforces this through declared PK/FK/UNIQUE constraints; the suffixes themselves remain conventions rather than hard-coded behavior.

## Acceptance criteria

```bash
./scripts/test.sh
./scripts/schemacsv validate examples/basic
```

Both must succeed. The intentionally invalid FK example must fail validation.
