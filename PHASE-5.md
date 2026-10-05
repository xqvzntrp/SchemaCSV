# Phase 5 — Interactive Editor

SchemaCSV v0.7.0 adds a staged terminal editor on top of the Phase 1–4 relational engine.

## Scope

- Start an interactive editing session with `edit-ui`.
- Browse tables and rows without leaving the editor.
- Stage inserts, updates, and deletes in memory.
- Navigate outgoing and incoming foreign-key relationships against staged data.
- Validate each mutation immediately and roll back only the invalid command.
- Keep the entire session unsaved until an explicit `save`.
- Discard all staged changes with `quit` or end-of-file.
- Reuse Phase 2 atomic persistence when saving.
- Support quoted values containing spaces.

## Start an editor session

```bash
./scripts/schemacsv edit-ui examples/basic
```

## Commands

```text
tables
use <table>
rows [column=value ...]
add column=value ...
edit column=value ... --set column=value ...
delete [--cascade] column=value ...
refs column=value ...
children column=value ...
validate
save
quit
```

## Transaction behavior

The editor opens one transaction for the session. `add`, `edit`, and `delete` mutate only that staged transaction. Each mutation is followed by relational validation; if it creates a PK, FK, UNIQUE, or required-value violation, that command is reverted immediately and the session continues with the previously valid staged state.

`save` performs the normal Phase 2 atomic persistence path and exits. `quit` closes the transaction without persistence.

## Acceptance criteria

- Existing Phase 1–4 behavior remains valid.
- Shared test suite increases from 15 to 20 tests.
- Invalid interactive edits are rejected without corrupting staged state.
- `quit` discards edits and leaves source CSVs unchanged.
- `save` persists staged edits atomically.
- Relationship navigation sees staged edits before save.
- Quoted values containing spaces can be entered interactively.
