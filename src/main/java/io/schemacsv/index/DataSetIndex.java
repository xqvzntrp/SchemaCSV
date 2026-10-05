package io.schemacsv.index;

import io.schemacsv.csv.*;
import io.schemacsv.model.*;
import io.schemacsv.schema.*;
import java.util.*;

/** Immutable, in-memory hash indexes derived from schema constraints. */
public final class DataSetIndex {
    private final DataSet dataSet;
    private final Map<String,List<Index>> byTable;

    public DataSetIndex(DataSet dataSet) {
        this.dataSet = Objects.requireNonNull(dataSet);
        LinkedHashMap<String,List<Index>> map = new LinkedHashMap<>();
        Map<String,LinkedHashMap<String,IndexDefinition>> defs = definitions(dataSet);
        for (DataTable table : dataSet.tables()) {
            List<Index> indexes = new ArrayList<>();
            for (IndexDefinition def : defs.getOrDefault(table.schema().name(), new LinkedHashMap<>()).values()) {
                indexes.add(build(table, def));
            }
            map.put(table.schema().name(), List.copyOf(indexes));
        }
        byTable = Collections.unmodifiableMap(map);
    }

    public List<CsvRecord> findExact(String tableName, Map<String,String> where) {
        DataTable table = dataSet.requireTable(tableName);
        if (where == null || where.isEmpty()) return table.csv().records();
        Index best = null;
        for (Index index : byTable.getOrDefault(tableName, List.of())) {
            if (!where.keySet().containsAll(index.definition.columns())) continue;
            if (best == null || index.definition.columns().size() > best.definition.columns().size()) best = index;
        }
        if (best == null) return table.csv().records();
        Key key = keyFromMap(where, best.definition.columns());
        return best.rows.getOrDefault(key, List.of());
    }

    public List<CsvRecord> findByColumns(String tableName, List<String> columns, List<String> values) {
        if (columns.size() != values.size()) throw new IllegalArgumentException("columns/values size mismatch");
        for (Index index : byTable.getOrDefault(tableName, List.of())) {
            if (index.definition.columns().equals(columns)) {
                return index.rows.getOrDefault(new Key(values.stream().map(String::trim).toList()), List.of());
            }
        }
        DataTable table=dataSet.requireTable(tableName);
        List<CsvRecord> out=new ArrayList<>();
        for(CsvRecord r:table.csv().records()) {
            boolean match=true;
            for(int i=0;i<columns.size();i++) if(!table.csv().get(r,columns.get(i)).trim().equals(values.get(i).trim())) {match=false;break;}
            if(match) out.add(r);
        }
        return List.copyOf(out);
    }

    public Map<Key,List<CsvRecord>> groups(String tableName, List<String> columns) {
        for(Index index:byTable.getOrDefault(tableName,List.of())) {
            if(index.definition.columns().equals(columns)) return index.rows;
        }
        DataTable table=dataSet.requireTable(tableName);
        LinkedHashMap<Key,List<CsvRecord>> rows=new LinkedHashMap<>();
        for(CsvRecord r:table.csv().records()) rows.computeIfAbsent(key(table.csv(),r,columns),k->new ArrayList<>()).add(r);
        LinkedHashMap<Key,List<CsvRecord>> frozen=new LinkedHashMap<>();
        for(var e:rows.entrySet()) frozen.put(e.getKey(),List.copyOf(e.getValue()));
        return Collections.unmodifiableMap(frozen);
    }

    public Optional<IndexStats> bestIndex(String tableName, Set<String> whereColumns) {
        Index best=null;
        for(Index index:byTable.getOrDefault(tableName,List.of())) {
            if(!whereColumns.containsAll(index.definition.columns())) continue;
            if(best==null||index.definition.columns().size()>best.definition.columns().size()) best=index;
        }
        return best==null?Optional.empty():Optional.of(best.stats());
    }

    public List<IndexStats> stats() {
        List<IndexStats> out=new ArrayList<>();
        for(List<Index> indexes:byTable.values()) for(Index i:indexes) out.add(i.stats());
        return List.copyOf(out);
    }

    public int indexCount() { return byTable.values().stream().mapToInt(List::size).sum(); }

    private static Index build(DataTable table, IndexDefinition def) {
        LinkedHashMap<Key,List<CsvRecord>> rows=new LinkedHashMap<>();
        for(CsvRecord r:table.csv().records()) {
            Key key=key(table.csv(),r,def.columns());
            rows.computeIfAbsent(key,k->new ArrayList<>()).add(r);
        }
        LinkedHashMap<Key,List<CsvRecord>> frozen=new LinkedHashMap<>();
        for(var e:rows.entrySet()) frozen.put(e.getKey(),List.copyOf(e.getValue()));
        return new Index(def,Collections.unmodifiableMap(frozen),table.csv().records().size());
    }

    private static Map<String,LinkedHashMap<String,IndexDefinition>> definitions(DataSet ds) {
        LinkedHashMap<String,LinkedHashMap<String,IndexDefinition>> out=new LinkedHashMap<>();
        for(DataTable t:ds.tables()) out.put(t.schema().name(),new LinkedHashMap<>());
        for(DataTable t:ds.tables()) {
            String table=t.schema().name();
            PrimaryKeySchema pk=t.schema().primaryKey();
            if(pk!=null) add(out, new IndexDefinition(table,pk.name(),"PRIMARY",pk.columns()));
            for(UniqueKeySchema uk:t.schema().uniqueKeys()) add(out,new IndexDefinition(table,uk.name(),"UNIQUE",uk.columns()));
            for(ForeignKeySchema fk:t.schema().foreignKeys()) {
                add(out,new IndexDefinition(table,fk.name()+"_child","FOREIGN_CHILD",fk.columns()));
                add(out,new IndexDefinition(fk.referencedTable(),fk.name()+"_parent","FOREIGN_PARENT",fk.referencedColumns()));
            }
        }
        return out;
    }

    private static void add(Map<String,LinkedHashMap<String,IndexDefinition>> out, IndexDefinition def) {
        String signature=String.join("\u001f",def.columns());
        LinkedHashMap<String,IndexDefinition> defs=out.computeIfAbsent(def.table(),k->new LinkedHashMap<>());
        // One physical hash index per exact column tuple. Prefer PRIMARY/UNIQUE names over derived FK aliases.
        IndexDefinition existing=defs.get(signature);
        if(existing==null || rank(def.kind())<rank(existing.kind())) defs.put(signature,def);
    }

    private static int rank(String kind) {
        return switch(kind){case "PRIMARY"->0;case "UNIQUE"->1;case "FOREIGN_PARENT"->2;default->3;};
    }

    private static Key key(CsvTable csv,CsvRecord row,List<String> columns) {
        return new Key(columns.stream().map(c->csv.get(row,c).trim()).toList());
    }
    private static Key keyFromMap(Map<String,String> values,List<String> columns) {
        return new Key(columns.stream().map(c->values.get(c).trim()).toList());
    }

    private static final class Index {
        private final IndexDefinition definition;
        private final Map<Key,List<CsvRecord>> rows;
        private final int indexedRows;
        private Index(IndexDefinition definition,Map<Key,List<CsvRecord>> rows,int indexedRows){this.definition=definition;this.rows=rows;this.indexedRows=indexedRows;}
        private IndexStats stats(){return new IndexStats(definition.table(),definition.name(),definition.kind(),definition.columns(),rows.size(),indexedRows);}
    }
}
