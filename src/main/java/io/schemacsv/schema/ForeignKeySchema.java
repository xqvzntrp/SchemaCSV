package io.schemacsv.schema;

import java.util.List;

public record ForeignKeySchema(String name, List<String> columns, String referencedTable, List<String> referencedColumns) {
    public ForeignKeySchema {
        columns = List.copyOf(columns);
        referencedColumns = List.copyOf(referencedColumns);
        if (columns.size() != referencedColumns.size()) throw new IllegalArgumentException("Foreign-key column counts differ for " + name);
    }
}
