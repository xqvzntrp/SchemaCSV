package io.schemacsv.validation;

import io.schemacsv.csv.*;
import io.schemacsv.model.*;
import io.schemacsv.schema.*;
import io.schemacsv.index.DataSetIndex;
import java.util.*;

public final class ConstraintValidator {
    private ConstraintValidator() {}

    public static ValidationResult validate(DataSet ds) {
        List<ConstraintViolation> out=new ArrayList<>();
        validateHeadersAndRequired(ds,out);
        validatePrimaryKeys(ds,out);
        validateUniqueKeys(ds,out);
        validateForeignKeys(ds,out);
        validateChecks(ds,out);
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
        DataSetIndex indexes=ds.index();
        for(DataTable dt:ds.tables()) {
            PrimaryKeySchema pk=dt.schema().primaryKey(); if(pk==null)continue;
            if(pk.columns().stream().anyMatch(c->!dt.csv().hasColumn(c))) continue;
            for(var e:indexes.groups(dt.schema().name(),pk.columns()).entrySet()) {
                Key key=e.getKey(); List<CsvRecord> rows=e.getValue();
                for(CsvRecord r:rows) if(key.parts().stream().anyMatch(String::isBlank))
                    out.add(new ConstraintViolation("PRIMARY_KEY_NULL","Primary key '"+pk.name()+"' contains an empty value: "+key,dt.csv().path(),r.lineNumber()));
                if(!key.parts().stream().anyMatch(String::isBlank) && rows.size()>1) {
                    int first=rows.getFirst().lineNumber();
                    for(int i=1;i<rows.size();i++) out.add(new ConstraintViolation("PRIMARY_KEY_DUPLICATE","Duplicate primary key '"+pk.name()+"' "+key+"; first seen at line "+first,dt.csv().path(),rows.get(i).lineNumber()));
                }
            }
        }
    }

    private static void validateUniqueKeys(DataSet ds,List<ConstraintViolation> out) {
        DataSetIndex indexes=ds.index();
        for(DataTable dt:ds.tables()) for(UniqueKeySchema uk:dt.schema().uniqueKeys()) {
            if(uk.columns().stream().anyMatch(c->!dt.csv().hasColumn(c))) continue;
            for(var e:indexes.groups(dt.schema().name(),uk.columns()).entrySet()) {
                Key key=e.getKey(); List<CsvRecord> rows=e.getValue();
                if(key.parts().stream().anyMatch(String::isBlank) || rows.size()<2) continue;
                int first=rows.getFirst().lineNumber();
                for(int i=1;i<rows.size();i++) out.add(new ConstraintViolation("UNIQUE_KEY_DUPLICATE","Duplicate unique key '"+uk.name()+"' "+key+"; first seen at line "+first,dt.csv().path(),rows.get(i).lineNumber()));
            }
        }
    }

    private static void validateForeignKeys(DataSet ds,List<ConstraintViolation> out) {
        DataSetIndex indexes=ds.index();
        for(DataTable child:ds.tables()) for(ForeignKeySchema fk:child.schema().foreignKeys()) {
            DataTable parent=ds.requireTable(fk.referencedTable());
            if(fk.columns().stream().anyMatch(c->!child.csv().hasColumn(c)) || fk.referencedColumns().stream().anyMatch(c->!parent.csv().hasColumn(c))) continue;
            for(CsvRecord cr:child.csv().records()) {
                Key k=key(child.csv(),cr,fk.columns());
                if(k.parts().stream().allMatch(String::isBlank)) continue;
                if(indexes.findByColumns(fk.referencedTable(),fk.referencedColumns(),k.parts()).isEmpty())
                    out.add(new ConstraintViolation("FOREIGN_KEY_MISSING_PARENT","Foreign key '"+fk.name()+"' "+k+" references "+fk.referencedTable()+"("+String.join(", ",fk.referencedColumns())+") but no matching parent row exists",child.csv().path(),cr.lineNumber()));
            }
        }
    }
    private static void validateChecks(DataSet ds,List<ConstraintViolation> out) {
        for(DataTable dt:ds.tables()) for(CheckConstraintSchema check:dt.schema().checks()) {
            if(!dt.csv().hasColumn(check.column())) continue;
            for(CsvRecord r:dt.csv().records()) {
                String value=dt.csv().get(r,check.column());
                if(value.isBlank()) continue;
                boolean ok;
                try { ok=check(value,check.operator(),check.operand()); }
                catch(RuntimeException e) {
                    out.add(new ConstraintViolation("CHECK_CONFIG_INVALID","Check constraint '"+check.name()+"' is invalid: "+e.getMessage(),dt.csv().path(),r.lineNumber()));
                    continue;
                }
                if(!ok) out.add(new ConstraintViolation("CHECK_VIOLATION","Check constraint '"+check.name()+"' failed for "+dt.schema().name()+"."+check.column()+"='"+value+"' ("+check.operator()+" "+check.operand()+")",dt.csv().path(),r.lineNumber()));
            }
        }
    }

    private static boolean check(String value,String operator,String operand) {
        return switch(operator) {
            case "IN" -> Arrays.stream(operand.split("\\|",-1)).anyMatch(value::equals);
            case "MATCHES" -> value.matches(operand);
            case "MIN_LENGTH" -> value.length() >= Integer.parseInt(operand);
            case "MAX_LENGTH" -> value.length() <= Integer.parseInt(operand);
            case "NUMERIC_MIN" -> new java.math.BigDecimal(value).compareTo(new java.math.BigDecimal(operand)) >= 0;
            case "NUMERIC_MAX" -> new java.math.BigDecimal(value).compareTo(new java.math.BigDecimal(operand)) <= 0;
            default -> throw new IllegalArgumentException("unsupported operator "+operator);
        };
    }

    private static Key key(CsvTable t,CsvRecord r,List<String> columns){return new Key(columns.stream().map(c->t.get(r,c).trim()).toList());}
}
