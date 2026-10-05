package io.schemacsv.cli;

import io.schemacsv.model.*;
import io.schemacsv.validation.*;
import java.nio.file.*;

public final class Main {
    public static void main(String[] args) {
        int code=run(args,System.out,System.err);
        if(code!=0) System.exit(code);
    }
    static int run(String[] args, java.io.PrintStream out, java.io.PrintStream err) {
        if(args.length!=2 || !args[0].equals("validate")) {
            err.println("Usage: schemacsv validate <dataset-directory>");
            return 2;
        }
        Path root=Path.of(args[1]).toAbsolutePath().normalize();
        try {
            DataSet ds=DataSetLoader.load(root);
            ValidationResult result=ConstraintValidator.validate(ds);
            out.println("SchemaCSV 0.1.0");
            out.printf("tables=%d rows=%d%n",ds.tables().size(),ds.rowCount());
            if(result.valid()) { out.println("validation=OK"); return 0; }
            out.printf("validation=FAILED violations=%d%n",result.violations().size());
            for(ConstraintViolation v:result.violations()) err.println(v);
            return 1;
        } catch(Exception e) {
            err.println("ERROR: "+e.getMessage());
            return 2;
        }
    }
}
