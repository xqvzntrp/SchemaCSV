package io.schemacsv.migration;

import io.schemacsv.csv.*;
import io.schemacsv.model.*;
import io.schemacsv.validation.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class SchemaMigration {
    private SchemaMigration() {}

    public static void renameTable(Path root, String oldName, String newName) throws Exception {
        migrate(root, staged -> {
            CsvTable tables=read(staged,"tables.csv");
            requireTable(tables, oldName); ensureNoTable(tables,newName);
            rewrite(staged,"tables.csv", row -> replace(row,"table_name",oldName,newName));
            rewrite(staged,"columns.csv", row -> replace(row,"table_name",oldName,newName));
            rewrite(staged,"primary_keys.csv", row -> replace(row,"table_name",oldName,newName));
            rewriteIfExists(staged,"unique_keys.csv", row -> replace(row,"table_name",oldName,newName));
            rewrite(staged,"foreign_keys.csv", row -> {
                Map<String,String> r=replace(row,"table_name",oldName,newName);
                return replace(r,"referenced_table",oldName,newName);
            });
        });
    }

    public static void renameColumn(Path root, String table, String oldName, String newName) throws Exception {
        migrate(root, staged -> {
            String dataFile=tableFile(staged,table);
            CsvTable columns=read(staged,"columns.csv");
            boolean found=false, conflict=false;
            for(CsvRecord r:columns.records()) if(columns.get(r,"table_name").equals(table)) {
                if(columns.get(r,"column_name").equals(oldName)) found=true;
                if(columns.get(r,"column_name").equals(newName)) conflict=true;
            }
            if(!found) throw new IllegalArgumentException("Unknown column: "+table+"."+oldName);
            if(conflict) throw new IllegalArgumentException("Column already exists: "+table+"."+newName);
            rewrite(staged,"columns.csv", row -> row.get("table_name").equals(table)?replace(row,"column_name",oldName,newName):row);
            rewrite(staged,"primary_keys.csv", row -> row.get("table_name").equals(table)?replace(row,"column_name",oldName,newName):row);
            rewriteIfExists(staged,"unique_keys.csv", row -> row.get("table_name").equals(table)?replace(row,"column_name",oldName,newName):row);
            rewrite(staged,"foreign_keys.csv", row -> {
                Map<String,String> r=row;
                if(row.get("table_name").equals(table)) r=replace(r,"column_name",oldName,newName);
                if(row.get("referenced_table").equals(table)) r=replace(r,"referenced_column",oldName,newName);
                return r;
            });
            renameDataColumn(staged.resolve("data").resolve(dataFile),oldName,newName);
        });
    }

    public static void addColumn(Path root, String table, String column, boolean required, String defaultValue) throws Exception {
        migrate(root, staged -> {
            String dataFile=tableFile(staged,table);
            CsvTable columns=read(staged,"columns.csv");
            int max=0; boolean conflict=false;
            for(CsvRecord r:columns.records()) if(columns.get(r,"table_name").equals(table)) {
                if(columns.get(r,"column_name").equals(column)) conflict=true;
                max=Math.max(max,Integer.parseInt(columns.get(r,"position")));
            }
            if(conflict) throw new IllegalArgumentException("Column already exists: "+table+"."+column);
            Path colPath=staged.resolve("schema/columns.csv");
            List<Map<String,String>> rows=rows(columns);
            rows.add(mapOf(columns.header(), Map.of("table_name",table,"column_name",column,"required",Boolean.toString(required),"position",Integer.toString(max+1))));
            CsvWriter.write(colPath,columns.header(),rows);
            Path data=staged.resolve("data").resolve(dataFile); CsvTable dt=CsvReader.read(data);
            if(dt.hasColumn(column)) throw new IllegalArgumentException("Data column already exists: "+column);
            if(required && defaultValue.isEmpty() && !dt.records().isEmpty()) throw new IllegalArgumentException("Required column needs a non-empty default for existing rows");
            List<String> header=new ArrayList<>(dt.header()); header.add(column);
            List<Map<String,String>> out=new ArrayList<>();
            for(Map<String,String> r:rows(dt)){ LinkedHashMap<String,String> n=new LinkedHashMap<>(r); n.put(column,defaultValue); out.add(n); }
            CsvWriter.write(data,header,out);
        });
    }

    public static void addUnique(Path root, String table, String keyName, List<String> columns) throws Exception {
        if(columns.isEmpty()) throw new IllegalArgumentException("At least one column is required");
        migrate(root, staged -> {
            tableFile(staged,table);
            CsvTable defs=read(staged,"columns.csv"); Set<String> known=new LinkedHashSet<>();
            for(CsvRecord r:defs.records()) if(defs.get(r,"table_name").equals(table)) known.add(defs.get(r,"column_name"));
            for(String c:columns) if(!known.contains(c)) throw new IllegalArgumentException("Unknown column: "+table+"."+c);
            Path path=staged.resolve("schema/unique_keys.csv");
            CsvTable uk=Files.exists(path)?CsvReader.read(path):new CsvTable(path,List.of("table_name","key_name","column_name","position"),List.of());
            for(CsvRecord r:uk.records()) if(uk.get(r,"table_name").equals(table)&&uk.get(r,"key_name").equals(keyName))
                throw new IllegalArgumentException("Unique key already exists: "+table+"."+keyName);
            List<Map<String,String>> rows=rows(uk);
            for(int i=0;i<columns.size();i++) rows.add(mapOf(uk.header(),Map.of("table_name",table,"key_name",keyName,"column_name",columns.get(i),"position",Integer.toString(i+1))));
            CsvWriter.write(path,uk.header(),rows);
        });
    }

    private static void migrate(Path root, Migration action) throws Exception {
        Path live=root.toAbsolutePath().normalize();
        Path tmp=live.resolve(".schemacsv-migrate.tmp"); deleteTree(tmp); Files.createDirectories(tmp);
        copyTree(live.resolve("schema"),tmp.resolve("schema")); copyTree(live.resolve("data"),tmp.resolve("data"));
        try {
            action.apply(tmp);
            DatasetMetadata.load(live).nextMigration().write(tmp);
            ValidationResult vr=ConstraintValidator.validate(DataSetLoader.load(tmp));
            if(!vr.valid()) throw new IllegalArgumentException("Migration would violate constraints: "+vr.violations().getFirst());
            replaceDir(live,"schema",tmp.resolve("schema")); replaceDir(live,"data",tmp.resolve("data"));
        } finally { deleteTree(tmp); }
    }

    private static void replaceDir(Path root,String name,Path staged)throws IOException{
        Path live=root.resolve(name), backup=root.resolve(".schemacsv-"+name+".backup"); deleteTree(backup);
        try { if(Files.exists(live)) Files.move(live,backup,StandardCopyOption.ATOMIC_MOVE); Files.move(staged,live,StandardCopyOption.ATOMIC_MOVE); deleteTree(backup); }
        catch(AtomicMoveNotSupportedException e){ if(Files.exists(live)&&!Files.exists(backup))Files.move(live,backup); Files.move(staged,live); deleteTree(backup); }
        catch(IOException e){ if(!Files.exists(live)&&Files.exists(backup))Files.move(backup,live); throw e; }
    }
    private static String tableFile(Path root,String table)throws IOException{ CsvTable t=read(root,"tables.csv"); for(CsvRecord r:t.records())if(t.get(r,"table_name").equals(table))return t.get(r,"file"); throw new IllegalArgumentException("Unknown table: "+table); }
    private static void requireTable(CsvTable t,String name){ for(CsvRecord r:t.records())if(t.get(r,"table_name").equals(name))return; throw new IllegalArgumentException("Unknown table: "+name); }
    private static void ensureNoTable(CsvTable t,String name){ for(CsvRecord r:t.records())if(t.get(r,"table_name").equals(name))throw new IllegalArgumentException("Table already exists: "+name); }
    private static CsvTable read(Path root,String file)throws IOException{return CsvReader.read(root.resolve("schema").resolve(file));}
    private static void rewriteIfExists(Path root,String file,RowEdit edit)throws IOException{if(Files.exists(root.resolve("schema").resolve(file)))rewrite(root,file,edit);}
    private static void rewrite(Path root,String file,RowEdit edit)throws IOException{ CsvTable t=read(root,file); List<Map<String,String>> out=new ArrayList<>(); for(Map<String,String> r:rows(t))out.add(edit.apply(r)); CsvWriter.write(root.resolve("schema").resolve(file),t.header(),out); }
    private static Map<String,String> replace(Map<String,String> row,String col,String oldV,String newV){ LinkedHashMap<String,String> n=new LinkedHashMap<>(row); if(Objects.equals(n.get(col),oldV))n.put(col,newV); return n; }
    private static List<Map<String,String>> rows(CsvTable t){ List<Map<String,String>> out=new ArrayList<>(); for(CsvRecord r:t.records()){LinkedHashMap<String,String> m=new LinkedHashMap<>();for(String c:t.header())m.put(c,t.get(r,c));out.add(m);}return out;}
    private static Map<String,String> mapOf(List<String> header,Map<String,String> values){LinkedHashMap<String,String> m=new LinkedHashMap<>();for(String h:header)m.put(h,values.getOrDefault(h,""));return m;}
    private static void renameDataColumn(Path path,String oldName,String newName)throws IOException{ CsvTable t=CsvReader.read(path); if(!t.hasColumn(oldName))throw new IllegalArgumentException("Data file missing column: "+oldName); if(t.hasColumn(newName))throw new IllegalArgumentException("Data file already contains column: "+newName); List<String> h=new ArrayList<>();for(String c:t.header())h.add(c.equals(oldName)?newName:c); List<Map<String,String>> out=new ArrayList<>();for(Map<String,String> r:rows(t)){LinkedHashMap<String,String> n=new LinkedHashMap<>();for(String c:t.header())n.put(c.equals(oldName)?newName:c,r.get(c));out.add(n);}CsvWriter.write(path,h,out); }
    private static void copyTree(Path from,Path to)throws IOException{try(var s=Files.walk(from)){for(Path p:s.toList()){Path q=to.resolve(from.relativize(p));if(Files.isDirectory(p))Files.createDirectories(q);else Files.copy(p,q,StandardCopyOption.REPLACE_EXISTING);}}}
    private static void deleteTree(Path p)throws IOException{if(!Files.exists(p))return;try(var s=Files.walk(p)){for(Path x:s.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(x);}}
    @FunctionalInterface private interface Migration{void apply(Path staged)throws Exception;}
    @FunctionalInterface private interface RowEdit{Map<String,String> apply(Map<String,String> row);}
}
