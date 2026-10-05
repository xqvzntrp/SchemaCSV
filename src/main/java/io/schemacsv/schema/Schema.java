package io.schemacsv.schema;

import java.util.*;

public final class Schema {
    private final Map<String, TableSchema> tables;
    public Schema(Collection<TableSchema> tables) {
        LinkedHashMap<String,TableSchema> map = new LinkedHashMap<>();
        for (TableSchema t : tables) {
            if (map.put(t.name(), t) != null) throw new IllegalArgumentException("Duplicate table schema: " + t.name());
        }
        this.tables = Collections.unmodifiableMap(map);
    }
    public Collection<TableSchema> tables() { return tables.values(); }
    public TableSchema requireTable(String name) {
        TableSchema t = tables.get(name);
        if (t == null) throw new IllegalArgumentException("Unknown table: " + name);
        return t;
    }
}
