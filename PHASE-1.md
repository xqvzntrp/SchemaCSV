# Phase 1 — Relational Validation

## Acceptance target

Phase 1 is complete when SchemaCSV can load a directory containing schema and data CSV files, validate required values plus primary- and foreign-key constraints (including composite keys), emit useful file/row diagnostics, and return a CI-friendly exit code.

## Intended commit sequence

1. `chore: initialize SchemaCSV project`
2. `feat: add generic schema and table model`
3. `feat: add CSV loading and schema parsing`
4. `feat: validate primary key constraints`
5. `feat: validate foreign key constraints`
6. `feat: add validate command`
7. `test: add relational validation fixtures`
8. `docs: document Phase 1 and project roadmap`

## Out of scope

Editing, transactional persistence, cascade/restrict mutations, query language, and interactive UI belong to later phases.
