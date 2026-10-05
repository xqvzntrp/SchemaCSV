package io.schemacsv.csv;

import java.nio.file.Path;
import java.util.*;

public final class CsvTable {
    private final Path path;
    private final List<String> header;
    private final Map<String, Integer> index;
    private final List<CsvRecord> records;

    public CsvTable(Path path, List<String> header, List<CsvRecord> records) {
        this.path = path;
        this.header = List.copyOf(header);
        this.records = List.copyOf(records);
        Map<String,Integer> m = new LinkedHashMap<>();
        for (int i=0;i<header.size();i++) {
            String name = header.get(i);
            if (m.put(name, i) != null) throw new IllegalArgumentException("Duplicate CSV column '" + name + "' in " + path);
        }
        this.index = Map.copyOf(m);
    }

    public Path path() { return path; }
    public List<String> header() { return header; }
    public List<CsvRecord> records() { return records; }
    public boolean hasColumn(String name) { return index.containsKey(name); }
    public String get(CsvRecord record, String column) {
        Integer i = index.get(column);
        if (i == null) throw new IllegalArgumentException("Unknown column '" + column + "' in " + path);
        return i < record.values().size() ? record.values().get(i) : "";
    }
}
