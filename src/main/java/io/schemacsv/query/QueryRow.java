package io.schemacsv.query;

import java.util.*;

public record QueryRow(String table, int lineNumber, Map<String,String> values) {
    public QueryRow {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
