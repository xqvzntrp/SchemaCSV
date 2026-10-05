package io.schemacsv.csv;

import java.util.List;

public record CsvRecord(int lineNumber, List<String> values) {
    public CsvRecord {
        values = List.copyOf(values);
    }
}
