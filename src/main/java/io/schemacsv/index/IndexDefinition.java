package io.schemacsv.index;

import java.util.List;

public record IndexDefinition(String table, String name, String kind, List<String> columns) {
    public IndexDefinition { columns = List.copyOf(columns); }
}
