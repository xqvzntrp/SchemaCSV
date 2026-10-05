package io.schemacsv.query;

import io.schemacsv.csv.*;
import io.schemacsv.model.*;
import io.schemacsv.schema.*;
import io.schemacsv.index.*;
import java.util.*;

public final class QueryEngine {
    private final DataSet dataSet;
    private final DataSetIndex index;

    public QueryEngine(DataSet dataSet) { this.dataSet = Objects.requireNonNull(dataSet); this.index=dataSet.index(); }

    public List<TableInfo> tables() {
        List<TableInfo> out = new ArrayList<>();
        for (DataTable table : dataSet.tables()) {
            out.add(new TableInfo(table.schema().name(), table.schema().file(), table.csv().records().size(),
                    table.schema().primaryKey().columns()));
        }
        return List.copyOf(out);
    }

    public List<QueryRow> select(String tableName, Map<String,String> where, List<String> columns, List<String> sortColumns) {
        DataTable table = dataSet.requireTable(tableName);
        CsvTable csv = table.csv();
        validateColumns(csv, where.keySet());
        List<String> projection = columns == null || columns.isEmpty() ? csv.header() : List.copyOf(columns);
        validateColumns(csv, projection);
        List<String> sort = sortColumns == null ? List.of() : List.copyOf(sortColumns);
        validateColumns(csv, sort);

        List<CsvRecord> records = index.findExact(tableName, where).stream().filter(r -> matches(csv, r, where)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (!sort.isEmpty()) records.sort(comparator(csv, sort));
        List<QueryRow> out = new ArrayList<>();
        for (CsvRecord record : records) out.add(toRow(tableName, csv, record, projection));
        return List.copyOf(out);
    }

    public QueryRow lookup(String tableName, Map<String,String> key) {
        if (key.isEmpty()) throw new IllegalArgumentException("lookup requires at least one key column");
        List<QueryRow> rows = select(tableName, key, List.of(), List.of());
        if (rows.isEmpty()) throw new IllegalArgumentException("No row found in table '" + tableName + "' for " + key);
        if (rows.size() > 1) throw new IllegalArgumentException("Lookup is ambiguous in table '" + tableName + "' for " + key + ": " + rows.size() + " rows matched");
        return rows.getFirst();
    }

    public List<RelationshipRow> references(String tableName, Map<String,String> key) {
        DataTable source = dataSet.requireTable(tableName);
        QueryRow row = lookup(tableName, key);
        List<RelationshipRow> out = new ArrayList<>();
        for (ForeignKeySchema fk : source.schema().foreignKeys()) {
            LinkedHashMap<String,String> parentKey = new LinkedHashMap<>();
            for (int i=0;i<fk.columns().size();i++) parentKey.put(fk.referencedColumns().get(i), row.values().get(fk.columns().get(i)));
            QueryRow parent = lookup(fk.referencedTable(), parentKey);
            out.add(new RelationshipRow(fk.name(), parent));
        }
        return List.copyOf(out);
    }

    public List<RelationshipRow> referencedBy(String tableName, Map<String,String> key) {
        QueryRow parent = lookup(tableName, key);
        List<RelationshipRow> out = new ArrayList<>();
        for (DataTable child : dataSet.tables()) {
            for (ForeignKeySchema fk : child.schema().foreignKeys()) {
                if (!fk.referencedTable().equals(tableName)) continue;
                LinkedHashMap<String,String> childFilter = new LinkedHashMap<>();
                for (int i=0;i<fk.columns().size();i++) childFilter.put(fk.columns().get(i), parent.values().get(fk.referencedColumns().get(i)));
                for (QueryRow row : select(child.schema().name(), childFilter, List.of(), List.of())) {
                    out.add(new RelationshipRow(fk.name(), row));
                }
            }
        }
        return List.copyOf(out);
    }

    public List<IndexStats> indexStats() { return index.stats(); }

    public String explainSelect(String tableName, Map<String,String> where) {
        dataSet.requireTable(tableName);
        if(where==null || where.isEmpty()) return "SCAN table="+tableName;
        return index.bestIndex(tableName,where.keySet())
                .map(s -> "INDEX table="+tableName+" name="+s.name()+" columns="+String.join(",",s.columns()))
                .orElse("SCAN table="+tableName);
    }

    private static boolean matches(CsvTable csv, CsvRecord record, Map<String,String> where) {
        for (var e : where.entrySet()) if (!csv.get(record, e.getKey()).equals(e.getValue())) return false;
        return true;
    }

    private static Comparator<CsvRecord> comparator(CsvTable csv, List<String> columns) {
        return (a,b) -> {
            for (String column : columns) {
                int c = csv.get(a,column).compareTo(csv.get(b,column));
                if (c != 0) return c;
            }
            return Integer.compare(a.lineNumber(), b.lineNumber());
        };
    }

    private static QueryRow toRow(String table, CsvTable csv, CsvRecord record, List<String> columns) {
        LinkedHashMap<String,String> values = new LinkedHashMap<>();
        for (String column : columns) values.put(column, csv.get(record,column));
        return new QueryRow(table, record.lineNumber(), values);
    }

    private static void validateColumns(CsvTable csv, Collection<String> columns) {
        for (String column : columns) if (!csv.hasColumn(column)) throw new IllegalArgumentException("Unknown column '" + column + "' in " + csv.path());
    }
}
