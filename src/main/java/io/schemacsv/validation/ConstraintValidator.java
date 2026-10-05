package io.schemacsv.validation;

import io.schemacsv.csv.*;
import io.schemacsv.model.*;
import io.schemacsv.schema.*;
import java.util.*;

public final class ConstraintValidator {
    private ConstraintValidator() {}

    public static ValidationResult validate(DataSet ds) {
        List<ConstraintViolation> out=new ArrayList<>();
        validateHeadersAndRequired(ds,out);
        validatePrimaryKeys(ds,out);
        validateUniqueKeys(ds,out);
        validateForeignKeys(ds,out);
        return new ValidationResult(out);
    }

    private static void validateHeadersAndRequired(DataSet ds,List<ConstraintViolation> out) {
        for(DataTable dt:ds.tables()) {
            TableSchema ts=dt.schema(); CsvTable csv=dt.csv();
            for(ColumnSchema c:ts.columns()) if(!csv.hasColumn(c.name())) out.add(new ConstraintViolation("MISSING_COLUMN","Table '"+ts.name()+"' requires column '"+c.name()+"'",csv.path(),1));
            for(CsvRecord r:csv.records()) for(ColumnSchema c:ts.columns()) if(c.required() && csv.hasColumn(c.name()) && csv.get(r,c.name()).trim().isEmpty())
                out.add(new ConstraintViolation("REQUIRED_VALUE","Required value is empty: "+ts.name()+"."+c.name(),csv.path(),r.lineNumber()));
        }
    }

    private static void validatePrimaryKeys(DataSet ds,List<ConstraintViolation> out) {
        for(DataTable dt:ds.tables()) {
            PrimaryKeySchema pk=dt.schema().primaryKey(); if(pk==null)continue;
            if(pk.columns().stream().anyMatch(c->!dt.csv().hasColumn(c))) continue;
            Map<Key,Integer> seen=new LinkedHashMap<>();
            for(CsvRecord r:dt.csv().records()) {
                Key key=key(dt.csv(),r,pk.columns());
                if(key.parts().stream().anyMatch(String::isBlank)) { out.add(new ConstraintViolation("PRIMARY_KEY_NULL","Primary key '"+pk.name()+"' contains an empty value: "+key,dt.csv().path(),r.lineNumber())); continue; }
                Integer first=seen.putIfAbsent(key,r.lineNumber());
                if(first!=null) out.add(new ConstraintViolation("PRIMARY_KEY_DUPLICATE","Duplicate primary key '"+pk.name()+"' "+key+"; first seen at line "+first,dt.csv().path(),r.lineNumber()));
            }
        }
    }

    private static void validateUniqueKeys(DataSet ds,List<ConstraintViolation> out) {
        for(DataTable dt:ds.tables()) for(UniqueKeySchema uk:dt.schema().uniqueKeys()) {
            if(uk.columns().stream().anyMatch(c->!dt.csv().hasColumn(c))) continue;
            Map<Key,Integer> seen=new LinkedHashMap<>();
            for(CsvRecord r:dt.csv().records()) {
                Key key=key(dt.csv(),r,uk.columns());
                if(key.parts().stream().anyMatch(String::isBlank)) continue;
                Integer first=seen.putIfAbsent(key,r.lineNumber());
                if(first!=null) out.add(new ConstraintViolation("UNIQUE_KEY_DUPLICATE","Duplicate unique key '"+uk.name()+"' "+key+"; first seen at line "+first,dt.csv().path(),r.lineNumber()));
            }
        }
    }

    private static void validateForeignKeys(DataSet ds,List<ConstraintViolation> out) {
        for(DataTable child:ds.tables()) for(ForeignKeySchema fk:child.schema().foreignKeys()) {
            DataTable parent=ds.requireTable(fk.referencedTable());
            if(fk.columns().stream().anyMatch(c->!child.csv().hasColumn(c)) || fk.referencedColumns().stream().anyMatch(c->!parent.csv().hasColumn(c))) continue;
            Set<Key> parentKeys=new HashSet<>();
            for(CsvRecord pr:parent.csv().records()) parentKeys.add(key(parent.csv(),pr,fk.referencedColumns()));
            for(CsvRecord cr:child.csv().records()) {
                Key k=key(child.csv(),cr,fk.columns());
                if(k.parts().stream().allMatch(String::isBlank)) continue;
                if(!parentKeys.contains(k)) out.add(new ConstraintViolation("FOREIGN_KEY_MISSING_PARENT","Foreign key '"+fk.name()+"' "+k+" references "+fk.referencedTable()+"("+String.join(", ",fk.referencedColumns())+") but no matching parent row exists",child.csv().path(),cr.lineNumber()));
            }
        }
    }
    private static Key key(CsvTable t,CsvRecord r,List<String> columns){return new Key(columns.stream().map(c->t.get(r,c).trim()).toList());}
}
