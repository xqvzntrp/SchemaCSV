package io.schemacsv;

import io.schemacsv.model.*;
import io.schemacsv.validation.*;
import java.nio.file.*;

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
        System.out.printf("TESTS: %d passed, 0 failed, %d total%n",passed,passed);
    }
    private static void test(String name, Throwing body) throws Exception { try { body.run(); passed++; System.out.println("PASS "+name); } catch(Throwable t){ System.err.println("FAIL "+name+": "+t.getMessage()); throw t; } }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    @FunctionalInterface interface Throwing { void run() throws Exception; }
}
