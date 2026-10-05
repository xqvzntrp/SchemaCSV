package io.schemacsv.index;

import java.util.List;

public record IndexStats(String table, String name, String kind, List<String> columns,
                         int distinctKeys, int indexedRows) {
    public IndexStats { columns = List.copyOf(columns); }
}
