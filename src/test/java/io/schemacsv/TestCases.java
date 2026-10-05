package io.schemacsv;

import io.schemacsv.edit.*;
import io.schemacsv.model.*;
import io.schemacsv.validation.*;
import io.schemacsv.query.*;

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
