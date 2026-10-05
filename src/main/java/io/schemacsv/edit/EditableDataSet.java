package io.schemacsv.edit;

import io.schemacsv.csv.*;
import io.schemacsv.model.*;
import io.schemacsv.schema.*;
import io.schemacsv.validation.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class EditableDataSet {
    private final Path root;
    private final Schema schema;
    private final LinkedHashMap<String,List<LinkedHashMap<String,String>>> rows;

    private EditableDataSet(Path root, Schema schema, LinkedHashMap<String,List<LinkedHashMap<String,String>>> rows) {
        this.root=root; this.schema=schema; this.rows=rows;
    }

    public static EditableDataSet load(Path root) throws IOException {
        Path normalized=root.toAbsolutePath().normalize();
        DataSet ds=DataSetLoader.load(normalized);
        LinkedHashMap<String,List<LinkedHashMap<String,String>>> rows=new LinkedHashMap<>();
        for (DataTable table:ds.tables()) {
            List<LinkedHashMap<String,String>> list=new ArrayList<>();
            for (CsvRecord record:table.csv().records()) {
                LinkedHashMap<String,String> row=new LinkedHashMap<>();
                for (String c:table.csv().header()) row.put(c,table.csv().get(record,c));
                list.add(row);
            }
            rows.put(table.schema().name(),list);
        }
        return new EditableDataSet(normalized,ds.schema(),rows);
    }

    public Transaction beginTransaction(){ return new Transaction(this, deepCopy(rows)); }
    public Schema schema(){ return schema; }
    public Path root(){ return root; }

    DataSet snapshot(Map<String,List<LinkedHashMap<String,String>>> source) {
        List<DataTable> tables=new ArrayList<>();
        for (TableSchema ts:schema.tables()) {
            List<String> header=ts.columns().stream().map(ColumnSchema::name).toList();
            List<CsvRecord> records=new ArrayList<>(); int line=2;
            for (Map<String,String> row:source.get(ts.name())) {
                List<String> values=header.stream().map(c->row.getOrDefault(c,"" )).toList();
                records.add(new CsvRecord(line++,values));
            }
            tables.add(new DataTable(ts,new CsvTable(root.resolve("data").resolve(ts.file()),header,records)));
        }
        return new DataSet(schema,tables);
    }

    void replace(Map<String,List<LinkedHashMap<String,String>>> replacement) {
        rows.clear(); rows.putAll(deepCopy(replacement));
    }

    static LinkedHashMap<String,List<LinkedHashMap<String,String>>> deepCopy(Map<String,List<LinkedHashMap<String,String>>> source) {
        LinkedHashMap<String,List<LinkedHashMap<String,String>>> out=new LinkedHashMap<>();
        source.forEach((k,v)->{ List<LinkedHashMap<String,String>> copy=new ArrayList<>(); for(var row:v)copy.add(new LinkedHashMap<>(row)); out.put(k,copy); });
        return out;
    }
}
