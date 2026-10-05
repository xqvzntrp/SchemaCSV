package io.schemacsv.schema;

import java.util.List;

public record UniqueKeySchema(String name, List<String> columns) {
    public UniqueKeySchema { columns = List.copyOf(columns); }
}
