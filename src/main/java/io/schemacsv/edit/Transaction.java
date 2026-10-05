package io.schemacsv.edit;

import io.schemacsv.model.*;
import io.schemacsv.schema.*;
import io.schemacsv.validation.*;
import java.util.*;

public final class Transaction implements AutoCloseable {
    private final EditableDataSet owner;
    private final LinkedHashMap<String,List<LinkedHashMap<String,String>>> rows;
    private boolean closed;

    Transaction(EditableDataSet owner, LinkedHashMap<String,List<LinkedHashMap<String,String>>> rows){this.owner=owner;this.rows=rows;}

    public Transaction insert(String table, Map<String,String> values){
        ensureOpen(); TableSchema ts=owner.schema().requireTable(table); LinkedHashMap<String,String> row=new LinkedHashMap<>();
        for(ColumnSchema c:ts.columns()) row.put(c.name(),values.getOrDefault(c.name(),""));
        for(String k:values.keySet()) if(!ts.columnNames().contains(k)) throw new EditException("Unknown column "+table+"."+k);
        rows.get(table).add(row); return this;
    }

    public Transaction update(String table, Map<String,String> key, Map<String,String> changes){
        ensureOpen(); TableSchema ts=owner.schema().requireTable(table); LinkedHashMap<String,String> row=requireRow(ts,key);
        Map<String,String> old=new LinkedHashMap<>(row);
        for(var e:changes.entrySet()) { if(!ts.columnNames().contains(e.getKey())) throw new EditException("Unknown column "+table+"."+e.getKey()); row.put(e.getKey(),e.getValue()); }
        propagateKeyChange(ts,old,row); return this;
    }

    public Transaction delete(String table, Map<String,String> key, DeleteRule rule){
        ensureOpen(); TableSchema ts=owner.schema().requireTable(table); LinkedHashMap<String,String> row=requireRow(ts,key);
        deleteReferenced(ts,row,rule,new HashSet<>()); rows.get(table).remove(row); return this;
    }

    public ValidationResult validate(){ ensureOpen(); return ConstraintValidator.validate(owner.snapshot(rows)); }

    /** Returns a read-only dataset snapshot of the current staged transaction state. */
    public DataSet snapshot(){ ensureOpen(); return owner.snapshot(rows); }

    /** Insert and keep the change only when the staged dataset remains valid. */
    public Transaction insertValidated(String table, Map<String,String> values){
        return validatedMutation(() -> insert(table, values));
    }

    /** Update and keep the change only when the staged dataset remains valid. */
    public Transaction updateValidated(String table, Map<String,String> key, Map<String,String> changes){
        return validatedMutation(() -> update(table, key, changes));
    }

    /** Delete and keep the change only when the staged dataset remains valid. */
    public Transaction deleteValidated(String table, Map<String,String> key, DeleteRule rule){
        return validatedMutation(() -> delete(table, key, rule));
    }

    public void commit(){
        ensureOpen(); ValidationResult result=validate();
        if(!result.valid()) throw new EditException("Transaction violates constraints: "+result.violations().getFirst());
        owner.replace(rows); closed=true;
    }

    public void commitAndSave() throws java.io.IOException {
        ensureOpen(); ValidationResult result=validate();
        if(!result.valid()) throw new EditException("Transaction violates constraints: "+result.violations().getFirst());
        DataSetPersistence.saveAtomically(owner.root(),owner.snapshot(rows)); owner.replace(rows); closed=true;
    }

    private LinkedHashMap<String,String> requireRow(TableSchema ts,Map<String,String> key){
        List<LinkedHashMap<String,String>> matches=rows.get(ts.name()).stream().filter(r->matches(r,key)).toList();
        if(matches.size()!=1) throw new EditException(matches.isEmpty()?"No row in "+ts.name()+" matches "+key:"Multiple rows in "+ts.name()+" match "+key);
        return matches.getFirst();
    }
    private static boolean matches(Map<String,String> row,Map<String,String> key){return key.entrySet().stream().allMatch(e->Objects.equals(row.get(e.getKey()),e.getValue()));}

    private void propagateKeyChange(TableSchema parent, Map<String,String> oldRow, Map<String,String> newRow){
        for(TableSchema child:owner.schema().tables()) for(ForeignKeySchema fk:child.foreignKeys()) if(fk.referencedTable().equals(parent.name())) {
            boolean referencedChanged=false; for(String c:fk.referencedColumns()) if(!Objects.equals(oldRow.get(c),newRow.get(c))) referencedChanged=true;
            if(!referencedChanged) continue;
            for(LinkedHashMap<String,String> childRow:rows.get(child.name())) {
                boolean match=true; for(int i=0;i<fk.columns().size();i++) if(!Objects.equals(childRow.get(fk.columns().get(i)),oldRow.get(fk.referencedColumns().get(i)))) {match=false;break;}
                if(match) {
                    Map<String,String> oldChild=new LinkedHashMap<>(childRow);
                    for(int i=0;i<fk.columns().size();i++) childRow.put(fk.columns().get(i),newRow.get(fk.referencedColumns().get(i)));
                    propagateKeyChange(child,oldChild,childRow);
                }
            }
        }
    }

    private void deleteReferenced(TableSchema parent, Map<String,String> parentRow, DeleteRule rule, Set<String> seen){
        String marker=parent.name()+":"+parentRow; if(!seen.add(marker)) return;
        for(TableSchema child:owner.schema().tables()) for(ForeignKeySchema fk:child.foreignKeys()) if(fk.referencedTable().equals(parent.name())) {
            List<LinkedHashMap<String,String>> dependent=new ArrayList<>();
            for(LinkedHashMap<String,String> childRow:rows.get(child.name())) {
                boolean match=true; for(int i=0;i<fk.columns().size();i++) if(!Objects.equals(childRow.get(fk.columns().get(i)),parentRow.get(fk.referencedColumns().get(i)))) {match=false;break;}
                if(match) dependent.add(childRow);
            }
            if(!dependent.isEmpty() && rule==DeleteRule.RESTRICT) throw new EditException("Delete restricted: "+dependent.size()+" row(s) in "+child.name()+" reference "+parent.name());
            if(rule==DeleteRule.CASCADE) for(var d:dependent){ deleteReferenced(child,d,rule,seen); rows.get(child.name()).remove(d); }
        }
    }

    private Transaction validatedMutation(Runnable mutation){
        ensureOpen();
        var before = EditableDataSet.deepCopy(rows);
        try {
            mutation.run();
            ValidationResult result = validate();
            if(!result.valid()) throw new EditException("Edit violates constraints: "+result.violations().getFirst());
            return this;
        } catch(RuntimeException e) {
            rows.clear(); rows.putAll(before);
            throw e;
        }
    }

    private void ensureOpen(){if(closed)throw new IllegalStateException("Transaction is closed");}
    @Override public void close(){closed=true;}
}
