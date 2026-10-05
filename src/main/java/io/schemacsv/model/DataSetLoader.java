package io.schemacsv.model;

import io.schemacsv.csv.CsvReader;
import io.schemacsv.schema.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class DataSetLoader {
    private DataSetLoader() {}
    public static DataSet load(Path root) throws IOException {
        Schema schema=SchemaLoader.load(root.resolve("schema"));
        List<DataTable> tables=new ArrayList<>();
        for(TableSchema ts:schema.tables()) tables.add(new DataTable(ts,CsvReader.read(root.resolve("data").resolve(ts.file()))));
        return new DataSet(schema,tables);
    }
}
