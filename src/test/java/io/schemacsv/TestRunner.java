package io.schemacsv;

import io.schemacsv.edit.*;
import io.schemacsv.model.*;
import io.schemacsv.validation.*;
import java.nio.file.*;
import java.util.*;

public final class TestRunner {
    private static int passed;
    public static void main(String[] args) throws Exception {
        Path repo=Path.of(System.getProperty("schemacsv.root","."));
        test("valid example passes",()->{
            var r=ConstraintValidator.validate(DataSetLoader.load(repo.resolve("examples/basic")));
            check(r.valid(),"expected valid dataset, got "+r.violations());
        });
        test("invalid foreign key is detected",()->{
            var r=ConstraintValidator.validate(DataSetLoader.load(repo.resolve("examples/invalid-fk")));
            check(!r.valid(),"expected invalid dataset");
            check(r.violations().stream().anyMatch(v->v.code().equals("FOREIGN_KEY_MISSING_PARENT")),"expected FK violation");
        });
        test("composite primary key is unique",()->{
            var ds=DataSetLoader.load(repo.resolve("examples/basic"));
            check(ds.schema().requireTable("category").primaryKey().columns().size()==2,"expected composite PK");
        });
        test("composite foreign key is defined",()->{
            var ds=DataSetLoader.load(repo.resolve("examples/basic"));
            var fk=ds.schema().requireTable("field").foreignKeys().getFirst();
            check(fk.columns().size()==2 && fk.referencedColumns().size()==2,"expected composite FK");
        });
        test("invalid insert is rejected at commit",()->withCopy(repo.resolve("examples/basic"), root->{
            var db=EditableDataSet.load(root);
            try(var tx=db.beginTransaction()) {
                tx.insert("field",Map.of("channel_code","WAYFAIR","category_code","MISSING","code","x","name","Bad"));
                boolean failed=false; try{tx.commit();}catch(EditException e){failed=true;}
                check(failed,"expected invalid FK insert to fail");
            }
        }));
        test("primary key rename cascades through relationship chain",()->withCopy(repo.resolve("examples/basic"), root->{
            var db=EditableDataSet.load(root);
            try(var tx=db.beginTransaction()) {
                tx.update("category",Map.of("channel_code","WAYFAIR","code","SECTIONALS_306"),Map.of("code","SECTIONALS_307"));
                tx.commitAndSave();
            }
            var ds=DataSetLoader.load(root); var result=ConstraintValidator.validate(ds); check(result.valid(),"renamed dataset should validate");
            var field=ds.requireTable("field").csv();
            check(field.records().stream().allMatch(r->field.get(r,"category_code").equals("SECTIONALS_307")),"field FKs should cascade");
            var value=ds.requireTable("value").csv();
            check(value.records().stream().allMatch(r->value.get(r,"category_code").equals("SECTIONALS_307")),"value FKs should cascade recursively");
        }));
        test("restrict blocks parent delete",()->withCopy(repo.resolve("examples/basic"), root->{
            var db=EditableDataSet.load(root); boolean failed=false;
            try(var tx=db.beginTransaction()) { try{tx.delete("category",Map.of("channel_code","WAYFAIR","code","SECTIONALS_306"),DeleteRule.RESTRICT);}catch(EditException e){failed=true;} }
            check(failed,"expected restricted delete to fail");
        }));
        test("cascade delete removes dependent rows",()->withCopy(repo.resolve("examples/basic"), root->{
            var db=EditableDataSet.load(root);
            try(var tx=db.beginTransaction()) { tx.delete("category",Map.of("channel_code","WAYFAIR","code","SECTIONALS_306"),DeleteRule.CASCADE); tx.commitAndSave(); }
            var ds=DataSetLoader.load(root); check(ConstraintValidator.validate(ds).valid(),"cascade result should validate");
            check(ds.requireTable("category").csv().records().isEmpty(),"category should be deleted");
            check(ds.requireTable("field").csv().records().isEmpty(),"fields should cascade");
            check(ds.requireTable("value").csv().records().isEmpty(),"values should cascade");
        }));
        System.out.printf("TESTS: %d passed, 0 failed, %d total%n",passed,passed);
    }
    private static void withCopy(Path source, ThrowingPath body) throws Exception {
        Path tmp=Files.createTempDirectory("schemacsv-test-"); copyTree(source,tmp); try{body.run(tmp);}finally{deleteTree(tmp);}
    }
    private static void copyTree(Path from,Path to)throws Exception{try(var s=Files.walk(from)){for(Path p:s.toList()){Path q=to.resolve(from.relativize(p));if(Files.isDirectory(p))Files.createDirectories(q);else Files.copy(p,q,StandardCopyOption.REPLACE_EXISTING);}}}
    private static void deleteTree(Path p)throws Exception{if(!Files.exists(p))return;try(var s=Files.walk(p)){for(Path x:s.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(x);}}
    private static void test(String name, Throwing body) throws Exception { try { body.run(); passed++; System.out.println("PASS "+name); } catch(Throwable t){ System.err.println("FAIL "+name+": "+t.getMessage()); throw t; } }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    @FunctionalInterface interface Throwing { void run() throws Exception; }
    @FunctionalInterface interface ThrowingPath { void run(Path path) throws Exception; }
}
