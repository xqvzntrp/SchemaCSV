package io.schemacsv.schema;

import io.schemacsv.csv.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class SchemaLoader {
    private SchemaLoader() {}

    public static Schema load(Path schemaDir) throws IOException {
        CsvTable tablesCsv = CsvReader.read(schemaDir.resolve("tables.csv"));
        CsvTable columnsCsv = CsvReader.read(schemaDir.resolve("columns.csv"));
        CsvTable pkCsv = CsvReader.read(schemaDir.resolve("primary_keys.csv"));
        CsvTable fkCsv = CsvReader.read(schemaDir.resolve("foreign_keys.csv"));
        Path uniquePath = schemaDir.resolve("unique_keys.csv");
        CsvTable uniqueCsv = Files.exists(uniquePath) ? CsvReader.read(uniquePath) : null;
        requireColumns(tablesCsv, "table_name", "file");
        requireColumns(columnsCsv, "table_name", "column_name", "required", "position");
        requireColumns(pkCsv, "table_name", "key_name", "column_name", "position");
        requireColumns(fkCsv, "table_name", "key_name", "column_name", "position", "referenced_table", "referenced_column");
        if (uniqueCsv != null) requireColumns(uniqueCsv, "table_name", "key_name", "column_name", "position");

        record TableDef(String name, String file) {}
        LinkedHashMap<String,TableDef> defs = new LinkedHashMap<>();
        for (CsvRecord r : tablesCsv.records()) {
            String name = req(tablesCsv,r,"table_name");
            String file = req(tablesCsv,r,"file");
            if (defs.put(name,new TableDef(name,file)) != null) throw new IllegalArgumentException("Duplicate table_name '"+name+"' at "+tablesCsv.path()+":"+r.lineNumber());
        }
        Map<String,List<Positioned<ColumnSchema>>> cols = new HashMap<>();
        for (CsvRecord r : columnsCsv.records()) {
            String table = req(columnsCsv,r,"table_name"); ensureTable(defs,table,columnsCsv,r);
            int pos = pos(columnsCsv,r);
            boolean required = parseBool(req(columnsCsv,r,"required"), columnsCsv, r);
            cols.computeIfAbsent(table,k->new ArrayList<>()).add(new Positioned<>(pos,new ColumnSchema(req(columnsCsv,r,"column_name"),required)));
        }
        Map<String,List<Positioned<String>>> pks = new HashMap<>();
        Map<String,String> pkNames = new HashMap<>();
        for (CsvRecord r : pkCsv.records()) {
            String table=req(pkCsv,r,"table_name"); ensureTable(defs,table,pkCsv,r);
            String key=req(pkCsv,r,"key_name");
            String prev=pkNames.putIfAbsent(table,key); if(prev!=null&&!prev.equals(key)) throw new IllegalArgumentException("Only one primary key is allowed per table: "+table);
            pks.computeIfAbsent(table,k->new ArrayList<>()).add(new Positioned<>(pos(pkCsv,r),req(pkCsv,r,"column_name")));
        }
        Map<String,Map<String,List<Positioned<String>>>> uniques = new HashMap<>();
        if (uniqueCsv != null) for (CsvRecord r : uniqueCsv.records()) {
            String table=req(uniqueCsv,r,"table_name"); ensureTable(defs,table,uniqueCsv,r);
            String key=req(uniqueCsv,r,"key_name");
            uniques.computeIfAbsent(table,k->new LinkedHashMap<>()).computeIfAbsent(key,k->new ArrayList<>())
                    .add(new Positioned<>(pos(uniqueCsv,r),req(uniqueCsv,r,"column_name")));
        }

        record FkPart(int position,String column,String refTable,String refColumn) {}
        Map<String,Map<String,List<FkPart>>> fks = new HashMap<>();
        for (CsvRecord r:fkCsv.records()) {
            String table=req(fkCsv,r,"table_name"); ensureTable(defs,table,fkCsv,r);
            String key=req(fkCsv,r,"key_name");
            fks.computeIfAbsent(table,k->new LinkedHashMap<>()).computeIfAbsent(key,k->new ArrayList<>())
                .add(new FkPart(pos(fkCsv,r),req(fkCsv,r,"column_name"),req(fkCsv,r,"referenced_table"),req(fkCsv,r,"referenced_column")));
        }

        List<TableSchema> schemas=new ArrayList<>();
        for(TableDef d:defs.values()) {
            List<ColumnSchema> columnList=sortedValues(cols.getOrDefault(d.name(),List.of()));
            Set<String> names=new LinkedHashSet<>(); for(ColumnSchema c:columnList) { if(!names.add(c.name())) throw new IllegalArgumentException("Duplicate column schema "+d.name()+"."+c.name()); }
            List<String> pkCols=sortedValues(pks.getOrDefault(d.name(),List.of()));
            PrimaryKeySchema pk=pkCols.isEmpty()?null:new PrimaryKeySchema(pkNames.get(d.name()),pkCols);
            List<UniqueKeySchema> uniqueList=new ArrayList<>();
            for(var e:uniques.getOrDefault(d.name(),Map.of()).entrySet())
                uniqueList.add(new UniqueKeySchema(e.getKey(), sortedValues(e.getValue())));
            List<ForeignKeySchema> fkList=new ArrayList<>();
            for(var e:fks.getOrDefault(d.name(),Map.of()).entrySet()) {
                List<FkPart> parts=new ArrayList<>(e.getValue()); parts.sort(Comparator.comparingInt(FkPart::position));
                String ref=parts.getFirst().refTable();
                if(parts.stream().anyMatch(p->!p.refTable().equals(ref))) throw new IllegalArgumentException("Foreign key "+e.getKey()+" references multiple tables");
                fkList.add(new ForeignKeySchema(e.getKey(), parts.stream().map(FkPart::column).toList(), ref, parts.stream().map(FkPart::refColumn).toList()));
            }
            validateColumns(d.name(),names,pk,uniqueList,fkList,defs.keySet());
            schemas.add(new TableSchema(d.name(),d.file(),columnList,pk,uniqueList,fkList));
        }
        return new Schema(schemas);
    }

    private static void validateColumns(String table,Set<String> names,PrimaryKeySchema pk,List<UniqueKeySchema> uniques,List<ForeignKeySchema> fks,Set<String> tableNames) {
        if(pk!=null) for(String c:pk.columns()) if(!names.contains(c)) throw new IllegalArgumentException("Primary key references unknown column "+table+"."+c);
        for(UniqueKeySchema uk:uniques) for(String c:uk.columns()) if(!names.contains(c)) throw new IllegalArgumentException("Unique key references unknown column "+table+"."+c);
        for(ForeignKeySchema fk:fks) {
            for(String c:fk.columns()) if(!names.contains(c)) throw new IllegalArgumentException("Foreign key references unknown local column "+table+"."+c);
            if(!tableNames.contains(fk.referencedTable())) throw new IllegalArgumentException("Foreign key "+fk.name()+" references unknown table "+fk.referencedTable());
        }
    }
    private record Positioned<T>(int position,T value) {}
    private static <T> List<T> sortedValues(List<Positioned<T>> in){ return in.stream().sorted(Comparator.comparingInt(Positioned::position)).map(Positioned::value).toList(); }
    private static int pos(CsvTable t,CsvRecord r){ try{return Integer.parseInt(req(t,r,"position"));}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid position at "+t.path()+":"+r.lineNumber());} }
    private static boolean parseBool(String v,CsvTable t,CsvRecord r){ return switch(v.toLowerCase(Locale.ROOT)){case "true","yes","1"->true;case "false","no","0"->false;default->throw new IllegalArgumentException("Invalid boolean '"+v+"' at "+t.path()+":"+r.lineNumber());}; }
    private static String req(CsvTable t,CsvRecord r,String c){String v=t.get(r,c).trim();if(v.isEmpty())throw new IllegalArgumentException("Missing "+c+" at "+t.path()+":"+r.lineNumber());return v;}
    private static void ensureTable(Map<String,?> defs,String name,CsvTable t,CsvRecord r){if(!defs.containsKey(name))throw new IllegalArgumentException("Unknown table '"+name+"' at "+t.path()+":"+r.lineNumber());}
    private static void requireColumns(CsvTable t,String... cs){for(String c:cs)if(!t.hasColumn(c))throw new IllegalArgumentException("Missing schema column '"+c+"' in "+t.path());}
}
