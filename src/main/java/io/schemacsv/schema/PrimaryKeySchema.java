package io.schemacsv.schema;

import java.util.List;

public record PrimaryKeySchema(String name, List<String> columns) {
    public PrimaryKeySchema { columns = List.copyOf(columns); }
}
