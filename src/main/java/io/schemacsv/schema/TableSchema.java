package io.schemacsv.schema;

import java.util.*;

public record TableSchema(String name, String file, List<ColumnSchema> columns,
                          PrimaryKeySchema primaryKey, List<UniqueKeySchema> uniqueKeys,
                          List<ForeignKeySchema> foreignKeys, List<DefaultValueSchema> defaults,
                          List<CheckConstraintSchema> checks) {
    public TableSchema {
        columns = List.copyOf(columns);
        uniqueKeys = List.copyOf(uniqueKeys);
        foreignKeys = List.copyOf(foreignKeys);
        defaults = List.copyOf(defaults);
        checks = List.copyOf(checks);
    }

    public Set<String> columnNames() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (ColumnSchema c : columns) out.add(c.name());
        return Collections.unmodifiableSet(out);
    }

    public Optional<String> defaultValue(String column) {
        return defaults.stream().filter(d -> d.column().equals(column)).map(DefaultValueSchema::value).findFirst();
    }
}
