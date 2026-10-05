package io.schemacsv.edit;

import io.schemacsv.csv.CsvWriter;
import io.schemacsv.model.*;
import io.schemacsv.schema.*;
import io.schemacsv.validation.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class DataSetPersistence {
    private DataSetPersistence() {}

    public static void saveAtomically(Path root, DataSet ds) throws IOException {
        ValidationResult result=ConstraintValidator.validate(ds);
        if(!result.valid()) throw new EditException("Refusing to save invalid dataset: "+result.violations().getFirst());

        Path parent=root.toAbsolutePath().normalize();
        Path data=parent.resolve("data");
        Path tmp=parent.resolve(".schemacsv-data.tmp");
        Path backup=parent.resolve(".schemacsv-data.backup");
        deleteTree(tmp); deleteTree(backup); Files.createDirectories(tmp);

        for(DataTable dt:ds.tables()) {
            List<Map<String,String>> rows=new ArrayList<>();
            for(var record:dt.csv().records()) {
                LinkedHashMap<String,String> row=new LinkedHashMap<>();
                for(String c:dt.csv().header()) row.put(c,dt.csv().get(record,c));
                rows.add(row);
            }
            CsvWriter.write(tmp.resolve(dt.schema().file()),dt.csv().header(),rows);
        }

        // Validate the staged bytes using the original schema before touching live data.
        Path verify=parent.resolve(".schemacsv-verify.tmp");
        deleteTree(verify); Files.createDirectories(verify);
        copyTree(parent.resolve("schema"),verify.resolve("schema"));
        copyTree(tmp,verify.resolve("data"));
        ValidationResult staged=ConstraintValidator.validate(DataSetLoader.load(verify));
        deleteTree(verify);
        if(!staged.valid()) { deleteTree(tmp); throw new EditException("Staged data failed reload validation: "+staged.violations().getFirst()); }

        try {
            if(Files.exists(data)) Files.move(data,backup,StandardCopyOption.ATOMIC_MOVE);
            Files.move(tmp,data,StandardCopyOption.ATOMIC_MOVE);
            deleteTree(backup);
        } catch (AtomicMoveNotSupportedException e) {
            if(Files.exists(data) && !Files.exists(backup)) Files.move(data,backup);
            Files.move(tmp,data);
            deleteTree(backup);
        } catch (IOException e) {
            if(!Files.exists(data) && Files.exists(backup)) Files.move(backup,data);
            throw e;
        } finally { deleteTree(tmp); }
    }

    private static void copyTree(Path from,Path to) throws IOException {
        try(var stream=Files.walk(from)) { for(Path p:stream.toList()) { Path q=to.resolve(from.relativize(p)); if(Files.isDirectory(p))Files.createDirectories(q);else Files.copy(p,q,StandardCopyOption.REPLACE_EXISTING); } }
    }
    private static void deleteTree(Path p) throws IOException {
        if(!Files.exists(p)) return;
        try(var stream=Files.walk(p)) { for(Path x:stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(x); }
    }
}
