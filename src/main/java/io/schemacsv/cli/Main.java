package io.schemacsv.cli;

import io.schemacsv.edit.*;
import io.schemacsv.model.*;
import io.schemacsv.validation.*;
import io.schemacsv.query.*;
import io.schemacsv.tui.TerminalEditor;
import io.schemacsv.migration.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class Main {
    private static final String VERSION="0.9.0";
    public static void main(String[] args) { int code=run(args,System.out,System.err); if(code!=0) System.exit(code); }

    static int run(String[] args, java.io.PrintStream out, java.io.PrintStream err) {
        if(args.length<2) { usage(err); return 2; }
        Path root=Path.of(args[1]).toAbsolutePath().normalize();
        try {
            return switch(args[0]) {
                case "validate" -> validate(root,out,err,args);
                case "insert" -> insert(root,out,args);
                case "update" -> update(root,out,args);
                case "delete" -> delete(root,out,args);
                case "tables" -> tables(root,out,args);
                case "select" -> select(root,out,args);
                case "lookup" -> lookup(root,out,args);
                case "references" -> references(root,out,args);
                case "referenced-by" -> referencedBy(root,out,args);
                case "edit-ui" -> interactiveEdit(root,out,err,args);
                case "version" -> version(root,out,args);
                case "history" -> history(root,out,args);
                case "migrate" -> migrate(root,out,args);
                default -> { usage(err); yield 2; }
            };
        } catch(Exception e) { err.println("ERROR: "+e.getMessage()); return 2; }
    }

    private static int validate(Path root,java.io.PrintStream out,java.io.PrintStream err,String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("validate accepts only <dataset-directory>");
        DataSet ds=DataSetLoader.load(root); ValidationResult result=ConstraintValidator.validate(ds);
        out.println("SchemaCSV "+VERSION); out.printf("tables=%d rows=%d%n",ds.tables().size(),ds.rowCount());
        if(result.valid()){out.println("validation=OK");return 0;}
        out.printf("validation=FAILED violations=%d%n",result.violations().size()); for(var v:result.violations())err.println(v); return 1;
    }

    private static int insert(Path root,java.io.PrintStream out,String[] args)throws Exception{
        if(args.length<4)throw new IllegalArgumentException("insert requires <dataset-directory> <table> column=value...");
        EditableDataSet db=EditableDataSet.load(root); Map<String,String> values=pairs(args,3,args.length);
        try(Transaction tx=db.beginTransaction()){tx.insert(args[2],values);tx.commitAndSave();}
        out.println("INSERT OK table="+args[2]); return 0;
    }

    private static int update(Path root,java.io.PrintStream out,String[] args)throws Exception{
        if(args.length<7)throw new IllegalArgumentException("update requires <dataset-directory> <table> --key ... --set ...");
        int keyAt=indexOf(args,"--key"), setAt=indexOf(args,"--set");
        if(keyAt!=3 || setAt<5)throw new IllegalArgumentException("Usage: schemacsv update <dir> <table> --key col=value... --set col=value...");
        Map<String,String> key=pairs(args,keyAt+1,setAt), changes=pairs(args,setAt+1,args.length);
        EditableDataSet db=EditableDataSet.load(root); try(Transaction tx=db.beginTransaction()){tx.update(args[2],key,changes);tx.commitAndSave();}
        out.println("UPDATE OK table="+args[2]); return 0;
    }

    private static int delete(Path root,java.io.PrintStream out,String[] args)throws Exception{
        if(args.length<4)throw new IllegalArgumentException("delete requires <dataset-directory> <table> [--cascade] key=value...");
        boolean cascade=args.length>3 && args[3].equals("--cascade"); int start=cascade?4:3;
        Map<String,String> key=pairs(args,start,args.length); EditableDataSet db=EditableDataSet.load(root);
        try(Transaction tx=db.beginTransaction()){tx.delete(args[2],key,cascade?DeleteRule.CASCADE:DeleteRule.RESTRICT);tx.commitAndSave();}
        out.println("DELETE OK table="+args[2]+" rule="+(cascade?"CASCADE":"RESTRICT")); return 0;
    }


    private static int tables(Path root, java.io.PrintStream out, String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("tables accepts only <dataset-directory>");
        QueryEngine q = new QueryEngine(DataSetLoader.load(root));
        out.println("table\tfile\trows\tprimary_key");
        for (TableInfo t : q.tables()) out.printf("%s\t%s\t%d\t%s%n", t.name(), t.file(), t.rows(), String.join(",", t.primaryKeyColumns()));
        return 0;
    }

    private static int select(Path root, java.io.PrintStream out, String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("select requires <dataset-directory> <table>");
        LinkedHashMap<String,String> where = new LinkedHashMap<>();
        List<String> columns = List.of(), sort = List.of();
        int i = 3;
        while (i < args.length) {
            switch (args[i]) {
                case "--where" -> {
                    int end = nextOption(args, i + 1);
                    where.putAll(pairs(args, i + 1, end));
                    i = end;
                }
                case "--columns" -> {
                    if (++i >= args.length) throw new IllegalArgumentException("--columns requires comma-separated names");
                    columns = commaList(args[i++]);
                }
                case "--sort" -> {
                    if (++i >= args.length) throw new IllegalArgumentException("--sort requires comma-separated names");
                    sort = commaList(args[i++]);
                }
                default -> throw new IllegalArgumentException("Unknown select option: " + args[i]);
            }
        }
        List<QueryRow> rows = new QueryEngine(DataSetLoader.load(root)).select(args[2], where, columns, sort);
        printRows(out, rows, columns);
        return 0;
    }

    private static int lookup(Path root, java.io.PrintStream out, String[] args) throws Exception {
        if (args.length < 4) throw new IllegalArgumentException("lookup requires <dataset-directory> <table> column=value...");
        QueryRow row = new QueryEngine(DataSetLoader.load(root)).lookup(args[2], pairs(args,3,args.length));
        printRows(out, List.of(row), List.of());
        return 0;
    }

    private static int references(Path root, java.io.PrintStream out, String[] args) throws Exception {
        if (args.length < 4) throw new IllegalArgumentException("references requires <dataset-directory> <table> column=value...");
        var rows = new QueryEngine(DataSetLoader.load(root)).references(args[2], pairs(args,3,args.length));
        printRelationships(out, rows);
        return 0;
    }

    private static int referencedBy(Path root, java.io.PrintStream out, String[] args) throws Exception {
        if (args.length < 4) throw new IllegalArgumentException("referenced-by requires <dataset-directory> <table> column=value...");
        var rows = new QueryEngine(DataSetLoader.load(root)).referencedBy(args[2], pairs(args,3,args.length));
        printRelationships(out, rows);
        return 0;
    }



    private static int version(Path root, java.io.PrintStream out, String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("version accepts only <dataset-directory>");
        DatasetMetadata m=DatasetMetadata.load(root);
        out.println("schema_version="+m.schemaVersion());
        out.println("dataset_version="+m.datasetVersion());
        return 0;
    }

    private static int history(Path root, java.io.PrintStream out, String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("history accepts only <dataset-directory>");
        Path path=root.resolve("schema/migrations.csv");
        if(!Files.exists(path)) { out.println("migrations=0"); return 0; }
        var csv=io.schemacsv.csv.CsvReader.read(path);
        out.println(String.join("\t",csv.header()));
        for(var r:csv.records()) {
            List<String> values=new ArrayList<>();
            for(String c:csv.header()) values.add(csv.get(r,c));
            out.println(String.join("\t",values));
        }
        out.println("migrations="+csv.records().size());
        return 0;
    }

    private static int migrate(Path root, java.io.PrintStream out, String[] args) throws Exception {
        if(args.length<3) throw new IllegalArgumentException("migrate requires an operation");
        switch(args[2]) {
            case "rename-table" -> {
                if(args.length!=5) throw new IllegalArgumentException("migrate rename-table <dir> <old> <new>");
                SchemaMigration.renameTable(root,args[3],args[4]);
            }
            case "rename-column" -> {
                if(args.length!=6) throw new IllegalArgumentException("migrate rename-column <dir> <table> <old> <new>");
                SchemaMigration.renameColumn(root,args[3],args[4],args[5]);
            }
            case "add-column" -> {
                if(args.length<5) throw new IllegalArgumentException("migrate add-column <dir> <table> <column> [--required] [--default value]");
                boolean required=false; String def="";
                for(int i=5;i<args.length;i++) {
                    if(args[i].equals("--required")) required=true;
                    else if(args[i].equals("--default")) { if(++i>=args.length) throw new IllegalArgumentException("--default requires a value"); def=args[i]; }
                    else throw new IllegalArgumentException("Unknown add-column option: "+args[i]);
                }
                SchemaMigration.addColumn(root,args[3],args[4],required,def);
            }
            case "add-unique" -> {
                if(args.length<6) throw new IllegalArgumentException("migrate add-unique <dir> <table> <key-name> <column[,column...]> ");
                SchemaMigration.addUnique(root,args[3],args[4],commaList(args[5]));
            }
            case "set-default" -> {
                if(args.length!=6) throw new IllegalArgumentException("migrate set-default <dir> <table> <column> <value>");
                SchemaMigration.setDefault(root,args[3],args[4],args[5]);
            }
            case "add-check" -> {
                if(args.length!=8) throw new IllegalArgumentException("migrate add-check <dir> <table> <name> <column> <operator> <operand>");
                SchemaMigration.addCheck(root,args[3],args[4],args[5],args[6],args[7]);
            }
            default -> throw new IllegalArgumentException("Unknown migration operation: "+args[2]);
        }
        DatasetMetadata m=DatasetMetadata.load(root);
        out.println("MIGRATION OK operation="+args[2]);
        out.println("schema_version="+m.schemaVersion()+" dataset_version="+m.datasetVersion());
        return 0;
    }

    private static int interactiveEdit(Path root, java.io.PrintStream out, java.io.PrintStream err, String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("edit-ui accepts only <dataset-directory>");
        return new TerminalEditor(root, new InputStreamReader(System.in), new OutputStreamWriter(out), new OutputStreamWriter(err)).run();
    }

    private static void printRows(java.io.PrintStream out, List<QueryRow> rows, List<String> requestedColumns) {
        if (rows.isEmpty()) { out.println("rows=0"); return; }
        List<String> columns = requestedColumns == null || requestedColumns.isEmpty() ? new ArrayList<>(rows.getFirst().values().keySet()) : requestedColumns;
        out.println(String.join("\t", columns));
        for (QueryRow row : rows) {
            List<String> values = new ArrayList<>();
            for (String column : columns) values.add(row.values().getOrDefault(column, ""));
            out.println(String.join("\t", values));
        }
        out.println("rows=" + rows.size());
    }

    private static void printRelationships(java.io.PrintStream out, List<RelationshipRow> rows) {
        if (rows.isEmpty()) { out.println("relationships=0"); return; }
        for (RelationshipRow rel : rows) {
            out.printf("[%s] %s line=%d%n", rel.relationship(), rel.row().table(), rel.row().lineNumber());
            for (var e : rel.row().values().entrySet()) out.printf("  %s=%s%n", e.getKey(), e.getValue());
        }
        out.println("relationships=" + rows.size());
    }

    private static List<String> commaList(String value) {
        if (value.isBlank()) return List.of();
        return Arrays.stream(value.split(",", -1)).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static int nextOption(String[] args, int from) {
        int i=from; while (i<args.length && !args[i].startsWith("--")) i++; return i;
    }

    private static Map<String,String> pairs(String[] args,int from,int to){
        if(from>=to)throw new IllegalArgumentException("At least one column=value pair is required"); LinkedHashMap<String,String> out=new LinkedHashMap<>();
        for(int i=from;i<to;i++){int p=args[i].indexOf('=');if(p<=0)throw new IllegalArgumentException("Expected column=value, got: "+args[i]);out.put(args[i].substring(0,p),args[i].substring(p+1));} return out;
    }
    private static int indexOf(String[] a,String value){for(int i=0;i<a.length;i++)if(a[i].equals(value))return i;return -1;}
    private static void usage(java.io.PrintStream err){
        err.println("Usage:");
        err.println("  schemacsv validate <dataset-directory>");
        err.println("  schemacsv insert <dataset-directory> <table> column=value...");
        err.println("  schemacsv update <dataset-directory> <table> --key column=value... --set column=value...");
        err.println("  schemacsv delete <dataset-directory> <table> [--cascade] column=value...");
        err.println("  schemacsv tables <dataset-directory>");
        err.println("  schemacsv select <dataset-directory> <table> [--where column=value...] [--columns a,b] [--sort a,b]");
        err.println("  schemacsv lookup <dataset-directory> <table> column=value...");
        err.println("  schemacsv references <dataset-directory> <table> column=value...");
        err.println("  schemacsv referenced-by <dataset-directory> <table> column=value...");
        err.println("  schemacsv edit-ui <dataset-directory>");
        err.println("  schemacsv version <dataset-directory>");
        err.println("  schemacsv history <dataset-directory>");
        err.println("  schemacsv migrate <dataset-directory> rename-table <old> <new>");
        err.println("  schemacsv migrate <dataset-directory> rename-column <table> <old> <new>");
        err.println("  schemacsv migrate <dataset-directory> add-column <table> <column> [--required] [--default value]");
        err.println("  schemacsv migrate <dataset-directory> add-unique <table> <key-name> <column[,column...]>");
        err.println("  schemacsv migrate <dataset-directory> set-default <table> <column> <value>");
        err.println("  schemacsv migrate <dataset-directory> add-check <table> <name> <column> <operator> <operand>");
    }
}
