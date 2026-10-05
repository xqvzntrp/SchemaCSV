package io.schemacsv;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

final class SchemaCsvTest {
    private final Path repo = Path.of(System.getProperty("schemacsv.root", ".")).toAbsolutePath().normalize();

    @Test void validExamplePasses() throws Exception { TestCases.validExamplePasses(repo); }
    @Test void invalidForeignKeyIsDetected() throws Exception { TestCases.invalidForeignKeyIsDetected(repo); }
    @Test void primaryKeysUseStableKeyColumns() throws Exception { TestCases.primaryKeysUseStableKeyColumns(repo); }
    @Test void businessCodesHaveScopedUniqueConstraints() throws Exception { TestCases.businessCodesHaveScopedUniqueConstraints(repo); }
    @Test void duplicateBusinessCodeIsRejected() throws Exception { TestCases.duplicateBusinessCodeIsRejected(repo); }
    @Test void invalidInsertIsRejectedAtCommit() throws Exception { TestCases.invalidInsertIsRejectedAtCommit(repo); }
    @Test void businessCodeRenameDoesNotChangeRelationalKeys() throws Exception { TestCases.businessCodeRenameDoesNotChangeRelationalKeys(repo); }
    @Test void trueKeyRenameCascadesToForeignKeys() throws Exception { TestCases.trueKeyRenameCascadesToForeignKeys(repo); }
    @Test void restrictBlocksParentDelete() throws Exception { TestCases.restrictBlocksParentDelete(repo); }
    @Test void cascadeDeleteRemovesDependentRows() throws Exception { TestCases.cascadeDeleteRemovesDependentRows(repo); }
    @Test void tableInspectionReportsSchemaAndCounts() throws Exception { TestCases.tableInspectionReportsSchemaAndCounts(repo); }
    @Test void selectSupportsFilterProjectionAndSort() throws Exception { TestCases.selectSupportsFilterProjectionAndSort(repo); }
    @Test void lookupFindsBusinessAwareRow() throws Exception { TestCases.lookupFindsBusinessAwareRow(repo); }
    @Test void referencesTraversesToParent() throws Exception { TestCases.referencesTraversesToParent(repo); }
    @Test void referencedByTraversesToChildren() throws Exception { TestCases.referencedByTraversesToChildren(repo); }
    @Test void validatedMutationRollsBackInvalidEdit() throws Exception { TestCases.validatedMutationRollsBackInvalidEdit(repo); }
    @Test void interactiveQuitDiscardsStagedChanges() throws Exception { TestCases.interactiveQuitDiscardsStagedChanges(repo); }
    @Test void interactiveSavePersistsChanges() throws Exception { TestCases.interactiveSavePersistsChanges(repo); }
    @Test void interactiveInvalidEditIsRejectedWithoutEndingSession() throws Exception { TestCases.interactiveInvalidEditIsRejectedWithoutEndingSession(repo); }
    @Test void interactiveEditorSupportsQuotedValuesAndNavigation() throws Exception { TestCases.interactiveEditorSupportsQuotedValuesAndNavigation(repo); }
    @Test void metadataReportsDatasetAndSchemaVersion() throws Exception { TestCases.metadataReportsDatasetAndSchemaVersion(repo); }
    @Test void renameColumnMigratesKeysAndForeignKeys() throws Exception { TestCases.renameColumnMigratesKeysAndForeignKeys(repo); }
    @Test void renameTableMigratesRelationshipReferences() throws Exception { TestCases.renameTableMigratesRelationshipReferences(repo); }
    @Test void addColumnAppliesDefaultAndAdvancesMetadata() throws Exception { TestCases.addColumnAppliesDefaultAndAdvancesMetadata(repo); }
    @Test void addUniqueConstraintIsValidated() throws Exception { TestCases.addUniqueConstraintIsValidated(repo); }
}
