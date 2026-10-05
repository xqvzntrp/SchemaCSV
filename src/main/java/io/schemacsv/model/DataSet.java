package io.schemacsv.model;

import io.schemacsv.schema.Schema;
import java.util.*;

public final class DataSet {
    private final Schema schema;
    private final Map<String,DataTable> tables;
    public DataSet(Schema schema, Collection<DataTable> tables) {
        this.schema=schema;
        LinkedHashMap<String,DataTable> map=new LinkedHashMap<>();
        for(DataTable t:tables) map.put(t.schema().name(),t);
        this.tables=Collections.unmodifiableMap(map);
    }
    public Schema schema(){return schema;}
    public Collection<DataTable> tables(){return tables.values();}
    public DataTable requireTable(String name){DataTable t=tables.get(name);if(t==null)throw new IllegalArgumentException("Unknown data table: "+name);return t;}
    public int rowCount(){return tables.values().stream().mapToInt(t->t.csv().records().size()).sum();}
}
