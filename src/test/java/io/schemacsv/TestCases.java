package io.schemacsv;

import io.schemacsv.edit.*;
import io.schemacsv.model.*;
import io.schemacsv.validation.*;
import io.schemacsv.query.*;
import io.schemacsv.tui.TerminalEditor;
import io.schemacsv.migration.*;
import java.io.*;

import java.nio.file.*;
import java.util.*;

public final class TestCases {
    private TestCases() {}

    public static void validExamplePasses(Path repo) throws Exception {
        var r = ConstraintValidator.validate(DataSetLoader.load(repo.resolve("examples/basic")));
        check(r.valid(), "expected valid dataset, got " + r.violations());
    }

    public static void invalidForeignKeyIsDetected(Path repo) throws Exception {
        var r = ConstraintValidator.validate(DataSetLoader.load(repo.resolve("examples/invalid-fk")));
        check(!r.valid(), "expected invalid dataset");
        check(r.violations().stream().anyMatch(v -> v.code().equals("FOREIGN_KEY_MISSING_PARENT")), "expected FK violation");
    }

    public static void primaryKeysUseStableKeyColumns(Path repo) throws Exception {
        var ds = DataSetLoader.load(repo.resolve("examples/basic"));
        check(ds.schema().requireTable("category").primaryKey().columns().equals(List.of("category_key")), "category PK should be category_key");
        check(ds.schema().requireTable("field").primaryKey().columns().equals(List.of("field_key")), "field PK should be field_key");
    }

    public static void businessCodesHaveScopedUniqueConstraints(Path repo) throws Exception {
        var ds = DataSetLoader.load(repo.resolve("examples/basic"));
        var uk = ds.schema().requireTable("category").uniqueKeys().getFirst();
        check(uk.columns().equals(List.of("channel_key", "category_code")), "expected scoped category code uniqueness");
    }

    public static void duplicateBusinessCodeIsRejected(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            var db = EditableDataSet.load(root);
            try (var tx = db.beginTransaction()) {
                tx.insert("category", Map.of("category_key", "101", "channel_key", "1", "category_code", "SECTIONALS_306", "name", "Duplicate"));
                boolean failed = false;
                try { tx.commit(); } catch (EditException e) { failed = true; }
                check(failed, "expected duplicate scoped code to fail");
            }
        });
    }

    public static void invalidInsertIsRejectedAtCommit(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            var db = EditableDataSet.load(root);
            try (var tx = db.beginTransaction()) {
                tx.insert("field", Map.of("field_key", "2000", "category_key", "999", "field_code", "x", "name", "Bad"));
                boolean failed = false;
                try { tx.commit(); } catch (EditException e) { failed = true; }
                check(failed, "expected invalid FK insert to fail");
            }
        });
    }

    public static void businessCodeRenameDoesNotChangeRelationalKeys(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            var db = EditableDataSet.load(root);
            try (var tx = db.beginTransaction()) {
                tx.update("category", Map.of("category_key", "100"), Map.of("category_code", "SECTIONALS_307"));
                tx.commitAndSave();
            }
            var ds = DataSetLoader.load(root);
            check(ConstraintValidator.validate(ds).valid(), "renamed dataset should validate");
            var category = ds.requireTable("category").csv();
            check(category.get(category.records().getFirst(), "category_key").equals("100"), "category_key must stay stable");
            var field = ds.requireTable("field").csv();
            check(field.records().stream().allMatch(r -> field.get(r, "category_key").equals("100")), "field FK should remain stable");
        });
    }

    public static void trueKeyRenameCascadesToForeignKeys(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            var db = EditableDataSet.load(root);
            try (var tx = db.beginTransaction()) {
                tx.update("category", Map.of("category_key", "100"), Map.of("category_key", "101"));
                tx.commitAndSave();
            }
            var ds = DataSetLoader.load(root);
            check(ConstraintValidator.validate(ds).valid(), "key-renamed dataset should validate");
            var field = ds.requireTable("field").csv();
            check(field.records().stream().allMatch(r -> field.get(r, "category_key").equals("101")), "field FK should cascade");
        });
    }

    public static void restrictBlocksParentDelete(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            var db = EditableDataSet.load(root);
            boolean failed = false;
            try (var tx = db.beginTransaction()) {
                try { tx.delete("category", Map.of("category_key", "100"), DeleteRule.RESTRICT); }
                catch (EditException e) { failed = true; }
            }
            check(failed, "expected restricted delete to fail");
        });
    }

    public static void cascadeDeleteRemovesDependentRows(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            var db = EditableDataSet.load(root);
            try (var tx = db.beginTransaction()) {
                tx.delete("category", Map.of("category_key", "100"), DeleteRule.CASCADE);
                tx.commitAndSave();
            }
            var ds = DataSetLoader.load(root);
            check(ConstraintValidator.validate(ds).valid(), "cascade result should validate");
            check(ds.requireTable("category").csv().records().isEmpty(), "category should be deleted");
            check(ds.requireTable("field").csv().records().isEmpty(), "fields should cascade");
            check(ds.requireTable("value").csv().records().isEmpty(), "values should cascade");
        });
    }

    public static void tableInspectionReportsSchemaAndCounts(Path repo) throws Exception {
        var q = new QueryEngine(DataSetLoader.load(repo.resolve("examples/basic")));
        var tables = q.tables();
        check(tables.size() == 4, "expected four tables");
        var field = tables.stream().filter(t -> t.name().equals("field")).findFirst().orElseThrow();
        check(field.rows() == 2, "expected two field rows");
        check(field.primaryKeyColumns().equals(List.of("field_key")), "expected field_key primary key");
    }

    public static void selectSupportsFilterProjectionAndSort(Path repo) throws Exception {
        var q = new QueryEngine(DataSetLoader.load(repo.resolve("examples/basic")));
        var rows = q.select("field", Map.of("category_key", "100"), List.of("field_code", "name"), List.of("field_code"));
        check(rows.size() == 2, "expected two selected fields");
        check(rows.getFirst().values().keySet().equals(new LinkedHashSet<>(List.of("field_code", "name"))), "projection order should be preserved");
        check(rows.getFirst().values().get("field_code").equals("core::brand"), "sort should put brand first");
    }

    public static void lookupFindsBusinessAwareRow(Path repo) throws Exception {
        var q = new QueryEngine(DataSetLoader.load(repo.resolve("examples/basic")));
        var row = q.lookup("category", Map.of("category_code", "SECTIONALS_306"));
        check(row.values().get("category_key").equals("100"), "lookup should return category key 100");
    }

    public static void referencesTraversesToParent(Path repo) throws Exception {
        var q = new QueryEngine(DataSetLoader.load(repo.resolve("examples/basic")));
        var rows = q.references("value", Map.of("value_key", "10000"));
        check(rows.size() == 1, "expected one parent reference");
        check(rows.getFirst().row().table().equals("field"), "value should reference field");
        check(rows.getFirst().row().values().get("field_key").equals("1000"), "expected field 1000");
    }

    public static void referencedByTraversesToChildren(Path repo) throws Exception {
        var q = new QueryEngine(DataSetLoader.load(repo.resolve("examples/basic")));
        var rows = q.referencedBy("category", Map.of("category_key", "100"));
        check(rows.size() == 2, "expected two referencing fields");
        check(rows.stream().allMatch(r -> r.row().table().equals("field")), "expected only field children");
    }


    public static void validatedMutationRollsBackInvalidEdit(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            var db = EditableDataSet.load(root);
            try (var tx = db.beginTransaction()) {
                boolean failed = false;
                try { tx.insertValidated("field", Map.of("field_key","9999","category_key","999","field_code","bad","name","Bad")); }
                catch (EditException e) { failed = true; }
                check(failed, "expected immediate integrity failure");
                check(new QueryEngine(tx.snapshot()).select("field", Map.of(), List.of(), List.of()).size() == 2,
                        "failed edit should be rolled back from staged state");
                check(tx.validate().valid(), "staged transaction should remain valid");
            }
        });
    }

    public static void interactiveQuitDiscardsStagedChanges(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            String input = "use category\nedit category_key=100 --set category_code=TEMP_CODE\nquit\n";
            var stdout = new StringWriter(); var stderr = new StringWriter();
            int code = new TerminalEditor(root, new StringReader(input), stdout, stderr).run();
            check(code == 0, "editor should exit cleanly");
            var row = new QueryEngine(DataSetLoader.load(root)).lookup("category", Map.of("category_key","100"));
            check(row.values().get("category_code").equals("SECTIONALS_306"), "quit must discard staged edits");
            check(stdout.toString().contains("staged changes discarded"), "quit should explain rollback");
        });
    }

    public static void interactiveSavePersistsChanges(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            String input = "use category\nedit category_key=100 --set category_code=SECTIONALS_307\nvalidate\nsave\n";
            var stdout = new StringWriter(); var stderr = new StringWriter();
            new TerminalEditor(root, new StringReader(input), stdout, stderr).run();
            var row = new QueryEngine(DataSetLoader.load(root)).lookup("category", Map.of("category_key","100"));
            check(row.values().get("category_code").equals("SECTIONALS_307"), "save should persist staged code rename");
            check(stdout.toString().contains("validation=OK"), "interactive validation should pass");
            check(stdout.toString().contains("SAVE OK"), "save should confirm success");
        });
    }

    public static void interactiveInvalidEditIsRejectedWithoutEndingSession(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            String input = "use field\nadd field_key=9999 category_key=999 field_code=bad name=Bad\nvalidate\nsave\n";
            var stdout = new StringWriter(); var stderr = new StringWriter();
            new TerminalEditor(root, new StringReader(input), stdout, stderr).run();
            check(stderr.toString().contains("Edit violates constraints"), "invalid interactive edit should report integrity error");
            check(stdout.toString().contains("validation=OK"), "session should remain valid after rejected edit");
            var rows = new QueryEngine(DataSetLoader.load(root)).select("field", Map.of(), List.of(), List.of());
            check(rows.size() == 2, "rejected edit must not be saved");
        });
    }

    public static void interactiveEditorSupportsQuotedValuesAndNavigation(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            String input = "use category\nedit category_key=100 --set name=\"Living Room Sectionals\"\nchildren category_key=100\nrows category_key=100\nsave\n";
            var stdout = new StringWriter(); var stderr = new StringWriter();
            new TerminalEditor(root, new StringReader(input), stdout, stderr).run();
            check(stderr.toString().isEmpty(), "quoted edit should not produce errors: " + stderr);
            check(stdout.toString().contains("relationships=2"), "children navigation should find two fields");
            check(stdout.toString().contains("Living Room Sectionals"), "staged row browsing should show quoted value");
            var row = new QueryEngine(DataSetLoader.load(root)).lookup("category", Map.of("category_key","100"));
            check(row.values().get("name").equals("Living Room Sectionals"), "quoted value should persist");
        });
    }


    public static void metadataReportsDatasetAndSchemaVersion(Path repo) throws Exception {
        var m = DatasetMetadata.load(repo.resolve("examples/basic"));
        check(m.schemaVersion() == 1, "expected schema version 1");
        check(m.datasetVersion() == 1, "expected dataset version 1");
    }

    public static void renameColumnMigratesKeysAndForeignKeys(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            SchemaMigration.renameColumn(root, "field", "field_key", "field_id");
            var ds = DataSetLoader.load(root);
            check(ds.schema().requireTable("field").primaryKey().columns().equals(List.of("field_id")), "PK should use renamed column");
            var valueFk = ds.schema().requireTable("value").foreignKeys().getFirst();
            check(valueFk.referencedColumns().equals(List.of("field_id")), "referencing FK should follow renamed parent column");
            check(ds.requireTable("field").csv().hasColumn("field_id"), "data header should be renamed");
            check(ConstraintValidator.validate(ds).valid(), "renamed schema should remain valid");
        });
    }

    public static void renameTableMigratesRelationshipReferences(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            SchemaMigration.renameTable(root, "field", "attribute");
            var ds = DataSetLoader.load(root);
            check(ds.schema().requireTable("attribute") != null, "renamed table should load");
            var fk = ds.schema().requireTable("value").foreignKeys().getFirst();
            check(fk.referencedTable().equals("attribute"), "FK should reference renamed table");
            check(ConstraintValidator.validate(ds).valid(), "renamed table dataset should remain valid");
        });
    }

    public static void addColumnAppliesDefaultAndAdvancesMetadata(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            SchemaMigration.addColumn(root, "category", "status", true, "ACTIVE");
            var ds = DataSetLoader.load(root);
            var row = new QueryEngine(ds).lookup("category", Map.of("category_key","100"));
            check(row.values().get("status").equals("ACTIVE"), "default should populate existing rows");
            var m=DatasetMetadata.load(root);
            check(m.schemaVersion()==2 && m.datasetVersion()==2, "migration should advance metadata versions");
            check(ConstraintValidator.validate(ds).valid(), "dataset should remain valid after add-column");
        });
    }

    public static void addUniqueConstraintIsValidated(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            SchemaMigration.addUnique(root, "field", "uq_field_name", List.of("category_key","name"));
            var ds=DataSetLoader.load(root);
            check(ds.schema().requireTable("field").uniqueKeys().stream().anyMatch(k -> k.name().equals("uq_field_name")), "unique key should be added");
            check(ConstraintValidator.validate(ds).valid(), "new unique constraint should validate");
        });
    }


    public static void persistentDefaultAppliesToFutureInsert(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            SchemaMigration.addColumn(root, "category", "status", true, "ACTIVE");
            EditableDataSet db=EditableDataSet.load(root);
            try(Transaction tx=db.beginTransaction()) {
                tx.insertValidated("category", Map.of("category_key","101","channel_key","1","category_code","SOFAS","name","Sofas"));
                tx.commitAndSave();
            }
            var row=new QueryEngine(DataSetLoader.load(root)).lookup("category", Map.of("category_key","101"));
            check(row.values().get("status").equals("ACTIVE"), "persistent default should apply to later inserts");
        });
    }

    public static void setDefaultSupportsRequiredColumns(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            SchemaMigration.setDefault(root,"category","name","Unnamed");
            EditableDataSet db=EditableDataSet.load(root);
            try(Transaction tx=db.beginTransaction()) {
                tx.insertValidated("category", Map.of("category_key","101","channel_key","1","category_code","SOFAS"));
                tx.commitAndSave();
            }
            var row=new QueryEngine(DataSetLoader.load(root)).lookup("category", Map.of("category_key","101"));
            check(row.values().get("name").equals("Unnamed"), "set-default should supply omitted required value");
        });
    }

    public static void checkConstraintRejectsInvalidEdit(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            SchemaMigration.addCheck(root,"category","ck_category_code","category_code","MATCHES","[A-Z0-9_]+");
            EditableDataSet db=EditableDataSet.load(root);
            try(Transaction tx=db.beginTransaction()) {
                boolean failed=false;
                try { tx.updateValidated("category",Map.of("category_key","100"),Map.of("category_code","bad-code")); }
                catch(EditException e) { failed=e.getMessage().contains("CHECK_VIOLATION"); }
                check(failed,"check constraint should reject invalid edit");
            }
        });
    }

    public static void migrationHistoryRecordsOperations(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            SchemaMigration.setDefault(root,"category","name","Unnamed");
            SchemaMigration.addCheck(root,"category","ck_name_len","name","MIN_LENGTH","2");
            var csv=io.schemacsv.csv.CsvReader.read(root.resolve("schema/migrations.csv"));
            check(csv.records().size()==2,"two migrations should be recorded");
            check(csv.get(csv.records().get(0),"operation").equals("set-default"),"first audit operation should be set-default");
            check(csv.get(csv.records().get(1),"operation").equals("add-check"),"second audit operation should be add-check");
            var meta=DatasetMetadata.load(root);
            check(meta.schemaVersion()==3 && meta.datasetVersion()==3,"audit migrations should advance versions");
        });
    }

    public static void renameColumnPropagatesDefaultAndCheckMetadata(Path repo) throws Exception {
        withCopy(repo.resolve("examples/basic"), root -> {
            SchemaMigration.setDefault(root,"category","name","Unnamed");
            SchemaMigration.addCheck(root,"category","ck_name_len","name","MIN_LENGTH","2");
            SchemaMigration.renameColumn(root,"category","name","display_name");
            var ds=DataSetLoader.load(root);
            var table=ds.schema().requireTable("category");
            check(table.defaultValue("display_name").orElseThrow().equals("Unnamed"),"default should follow renamed column");
            check(table.checks().stream().anyMatch(c->c.name().equals("ck_name_len")&&c.column().equals("display_name")),"check should follow renamed column");
            check(ConstraintValidator.validate(ds).valid(),"renamed default/check schema should remain valid");
        });
    }


    public static void indexCatalogCoversRelationalConstraints(Path repo) throws Exception {
        var ds=DataSetLoader.load(repo.resolve("examples/basic"));
        var stats=ds.index().stats();
        check(stats.size()==11,"expected 11 physical constraint indexes, got "+stats.size());
        check(stats.stream().anyMatch(i->i.table().equals("category")&&i.name().equals("pk_category")&&i.kind().equals("PRIMARY")),"category PK index missing");
        check(stats.stream().anyMatch(i->i.table().equals("field")&&i.columns().equals(List.of("category_key"))),"field FK child index missing");
    }

    public static void explainUsesPrimaryKeyIndex(Path repo) throws Exception {
        var q=new QueryEngine(DataSetLoader.load(repo.resolve("examples/basic")));
        String plan=q.explainSelect("field",Map.of("field_key","1000"));
        check(plan.contains("INDEX")&&plan.contains("pk_field"),"expected PK index plan, got "+plan);
    }

    public static void explainUsesForeignKeyIndex(Path repo) throws Exception {
        var q=new QueryEngine(DataSetLoader.load(repo.resolve("examples/basic")));
        String plan=q.explainSelect("field",Map.of("category_key","100"));
        check(plan.contains("INDEX")&&plan.contains("category_key"),"expected FK index plan, got "+plan);
    }

    public static void explainFallsBackToScanForUnindexedColumn(Path repo) throws Exception {
        var q=new QueryEngine(DataSetLoader.load(repo.resolve("examples/basic")));
        String plan=q.explainSelect("field",Map.of("name","Brand"));
        check(plan.startsWith("SCAN"),"expected scan plan for unindexed name, got "+plan);
    }

    public static void indexedQueriesPreserveResults(Path repo) throws Exception {
        var q=new QueryEngine(DataSetLoader.load(repo.resolve("examples/basic")));
        var rows=q.select("field",Map.of("category_key","100"),List.of("field_key","field_code"),List.of("field_key"));
        check(rows.size()==2,"expected two indexed field rows");
        check(rows.getFirst().values().get("field_key").equals("1000"),"expected deterministic indexed result ordering after sort");
        check(q.references("value",Map.of("value_key","10000")).getFirst().row().table().equals("field"),"indexed parent traversal should preserve relationship result");
    }


    private static void withCopy(Path source, ThrowingPath body) throws Exception {
        Path tmp = Files.createTempDirectory("schemacsv-test-");
        copyTree(source, tmp);
        try { body.run(tmp); } finally { deleteTree(tmp); }
    }

    private static void copyTree(Path from, Path to) throws Exception {
        try (var s = Files.walk(from)) {
            for (Path p : s.toList()) {
                Path q = to.resolve(from.relativize(p));
                if (Files.isDirectory(p)) Files.createDirectories(q);
                else Files.copy(p, q, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void deleteTree(Path p) throws Exception {
        if (!Files.exists(p)) return;
        try (var s = Files.walk(p)) {
            for (Path x : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(x);
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingPath { void run(Path path) throws Exception; }
}
