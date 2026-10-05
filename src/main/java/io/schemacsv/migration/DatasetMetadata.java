package io.schemacsv.migration;

import io.schemacsv.csv.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public record DatasetMetadata(int schemaVersion, int datasetVersion) {
    public static DatasetMetadata load(Path root) throws IOException {
        Path path = root.resolve("schema/metadata.csv");
        if (!Files.exists(path)) return new DatasetMetadata(1, 1);
        CsvTable csv = CsvReader.read(path);
        if (!csv.hasColumn("property") || !csv.hasColumn("value"))
            throw new IllegalArgumentException("metadata.csv requires property,value columns");
        Map<String,String> values = new LinkedHashMap<>();
        for (CsvRecord r : csv.records()) values.put(csv.get(r,"property").trim(), csv.get(r,"value").trim());
        return new DatasetMetadata(parse(values,"schema_version",1), parse(values,"dataset_version",1));
    }

    public void write(Path root) throws IOException {
        CsvWriter.write(root.resolve("schema/metadata.csv"), List.of("property","value"), List.of(
                Map.of("property","schema_version","value",Integer.toString(schemaVersion)),
                Map.of("property","dataset_version","value",Integer.toString(datasetVersion))));
    }

    public DatasetMetadata nextMigration() { return new DatasetMetadata(schemaVersion + 1, datasetVersion + 1); }
    private static int parse(Map<String,String> values, String key, int fallback) {
        String v=values.get(key); if(v==null||v.isBlank()) return fallback;
        try { return Integer.parseInt(v); } catch(NumberFormatException e) { throw new IllegalArgumentException("Invalid metadata value for "+key+": "+v); }
    }
}
