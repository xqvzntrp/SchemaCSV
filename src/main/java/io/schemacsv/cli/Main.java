package io.schemacsv.cli;

import io.schemacsv.edit.*;
import io.schemacsv.model.*;
import io.schemacsv.validation.*;
import java.nio.file.*;
import java.util.*;

public final class Main {
    private static final String VERSION="0.3.0";
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
    }
}
