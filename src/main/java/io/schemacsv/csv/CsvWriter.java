package io.schemacsv.csv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class CsvWriter {
    private CsvWriter() {}

    public static void write(Path path, List<String> header, List<? extends Map<String,String>> rows) throws IOException {
        Files.createDirectories(path.getParent());
        StringBuilder out = new StringBuilder();
        appendRow(out, header);
        for (Map<String,String> row : rows) {
            List<String> values = new ArrayList<>(header.size());
            for (String column : header) values.add(row.getOrDefault(column, ""));
            appendRow(out, values);
        }
        Files.writeString(path, out.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static void appendRow(StringBuilder out, List<String> values) {
        for (int i=0;i<values.size();i++) {
            if (i>0) out.append(',');
            out.append(escape(values.get(i)));
        }
        out.append('\n');
    }

    static String escape(String value) {
        String v = value == null ? "" : value;
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
