package io.schemacsv.query;

import java.util.List;

public record TableInfo(String name, String file, int rows, List<String> primaryKeyColumns) {
    public TableInfo { primaryKeyColumns = List.copyOf(primaryKeyColumns); }
}
