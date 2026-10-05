package io.schemacsv.schema;

import java.util.*;

public record TableSchema(String name, String file, List<ColumnSchema> columns,
                          PrimaryKeySchema primaryKey, List<UniqueKeySchema> uniqueKeys,
                          List<ForeignKeySchema> foreignKeys) {
    public TableSchema {
        columns = List.copyOf(columns);
        uniqueKeys = List.copyOf(uniqueKeys);
        foreignKeys = List.copyOf(foreignKeys);
    }

    public Set<String> columnNames() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (ColumnSchema c : columns) out.add(c.name());
        return Collections.unmodifiableSet(out);
    }
}
