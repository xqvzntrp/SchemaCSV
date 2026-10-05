# Phase 2 — Safe Editing and Persistence

SchemaCSV v0.2.0 adds mutation while preserving the Phase 1 guarantee that committed datasets satisfy their declared relational constraints.

## Delivered

- Transaction-style editing API
- Insert, update, and delete operations
- Primary-key and referenced-key updates
- Recursive propagation through foreign-key chains
- `RESTRICT` and explicit `CASCADE` deletion
- Deterministic CSV writer
- Staged reload validation before persistence
- Atomic replacement of the dataset `data/` directory when supported by the filesystem
- CLI commands for insert, update, and delete
- Tests for invalid writes, cascading key changes, restricted deletes, and cascading deletes

## Commit target

Phase 2 is intentionally split into focused commits:

1. `feat: add deterministic CSV writer`
2. `feat: add transactional insert update and delete`
3. `feat: add atomic validated CSV persistence`
4. `feat: cascade primary key changes through foreign keys`
5. `test: cover Phase 2 editing and persistence`
6. `feat: add Phase 2 editing CLI`
7. `docs: document Phase 2 editing workflow`

## Persistence guarantee

`commitAndSave()` validates the in-memory transaction, writes staged CSV files, reloads those staged bytes through the normal SchemaCSV loader, validates again, and only then replaces the live `data/` directory.

Schema files are not mutated in Phase 2. Schema evolution remains a later roadmap phase.

## Deferred to Phase 3+

- Query language and richer table inspection
- Relationship navigation commands
- Interactive terminal UI
- Schema migrations
- Secondary indexes and large-dataset optimization
