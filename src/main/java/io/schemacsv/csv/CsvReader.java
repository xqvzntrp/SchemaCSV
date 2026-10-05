package io.schemacsv.csv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class CsvReader {
    private CsvReader() {}

    public static CsvTable read(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        List<CsvRecord> rows = parse(text);
        if (rows.isEmpty()) throw new IllegalArgumentException("CSV is empty: " + path);
        List<String> header = rows.getFirst().values();
        if (header.isEmpty()) throw new IllegalArgumentException("CSV has no header: " + path);
        return new CsvTable(path, header, rows.subList(1, rows.size()));
    }

    static List<CsvRecord> parse(String text) {
        List<CsvRecord> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int rowLine = 1;
        for (int i=0;i<text.length();i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i+1 < text.length() && text.charAt(i+1) == '"') { field.append('"'); i++; }
                    else quoted = false;
                } else {
                    field.append(c);
                    if (c == '\n') line++;
                }
            } else {
                switch (c) {
                    case '"' -> quoted = true;
                    case ',' -> { row.add(field.toString()); field.setLength(0); }
                    case '\r' -> { }
                    case '\n' -> {
                        row.add(field.toString()); field.setLength(0);
                        if (!(row.size()==1 && row.getFirst().isEmpty())) rows.add(new CsvRecord(rowLine, row));
                        row = new ArrayList<>();
                        line++; rowLine = line;
                    }
                    default -> field.append(c);
                }
            }
        }
        if (quoted) throw new IllegalArgumentException("Unclosed quoted CSV field near line " + rowLine);
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(new CsvRecord(rowLine, row));
        }
        return rows;
    }
}
