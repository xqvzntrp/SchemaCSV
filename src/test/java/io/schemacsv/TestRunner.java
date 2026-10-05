package io.schemacsv;

import java.nio.file.Path;

public final class TestRunner {
    private static int passed;

    public static void main(String[] args) throws Exception {
        Path repo = Path.of(System.getProperty("schemacsv.root", "."));
        test("valid example passes", () -> TestCases.validExamplePasses(repo));
        test("invalid foreign key is detected", () -> TestCases.invalidForeignKeyIsDetected(repo));
        test("primary keys use stable key columns", () -> TestCases.primaryKeysUseStableKeyColumns(repo));
        test("business codes have scoped unique constraints", () -> TestCases.businessCodesHaveScopedUniqueConstraints(repo));
        test("duplicate business code is rejected", () -> TestCases.duplicateBusinessCodeIsRejected(repo));
        test("invalid insert is rejected at commit", () -> TestCases.invalidInsertIsRejectedAtCommit(repo));
        test("business code rename does not change relational keys", () -> TestCases.businessCodeRenameDoesNotChangeRelationalKeys(repo));
        test("true key rename cascades to foreign keys", () -> TestCases.trueKeyRenameCascadesToForeignKeys(repo));
        test("restrict blocks parent delete", () -> TestCases.restrictBlocksParentDelete(repo));
        test("cascade delete removes dependent rows", () -> TestCases.cascadeDeleteRemovesDependentRows(repo));
        test("table inspection reports schema and counts", () -> TestCases.tableInspectionReportsSchemaAndCounts(repo));
        test("select supports filter projection and sort", () -> TestCases.selectSupportsFilterProjectionAndSort(repo));
        test("lookup finds business-aware row", () -> TestCases.lookupFindsBusinessAwareRow(repo));
        test("references traverses to parent", () -> TestCases.referencesTraversesToParent(repo));
        test("referenced-by traverses to children", () -> TestCases.referencedByTraversesToChildren(repo));
        System.out.printf("TESTS: %d passed, 0 failed, %d total%n", passed, passed);
    }

    private static void test(String name, Throwing body) throws Exception {
        try {
            body.run();
            passed++;
            System.out.println("PASS " + name);
        } catch (Throwable t) {
            System.err.println("FAIL " + name + ": " + t.getMessage());
            throw t;
        }
    }

    @FunctionalInterface
    private interface Throwing { void run() throws Exception; }
}
