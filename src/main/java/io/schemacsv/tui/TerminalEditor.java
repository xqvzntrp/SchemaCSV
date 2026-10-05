package io.schemacsv.tui;

import io.schemacsv.edit.*;
import io.schemacsv.model.DataSetLoader;
import io.schemacsv.query.*;
import io.schemacsv.schema.TableSchema;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** A small line-oriented interactive editor over SchemaCSV transactions. */
public final class TerminalEditor {
    private final Path root;
    private final BufferedReader in;
    private final PrintWriter out;
    private final PrintWriter err;
    private String table;

    public TerminalEditor(Path root, Reader in, Writer out, Writer err) {
        this.root = root.toAbsolutePath().normalize();
        this.in = new BufferedReader(in);
        this.out = new PrintWriter(out, true);
        this.err = new PrintWriter(err, true);
    }

    public int run() throws Exception {
        EditableDataSet db = EditableDataSet.load(root);
        try (Transaction tx = db.beginTransaction()) {
            out.println("SchemaCSV interactive editor");
            out.println("dataset=" + root);
            out.println("Type 'help' for commands. Changes are staged until 'save'.");
            while (true) {
                out.print(table == null ? "schemacsv> " : "schemacsv[" + table + "]> ");
                out.flush();
                String line = in.readLine();
                if (line == null) { out.println("EOF: staged changes discarded"); return 0; }
                List<String> a;
                try { a = tokenize(line); }
                catch (IllegalArgumentException e) { err.println("ERROR: " + e.getMessage()); continue; }
                if (a.isEmpty()) continue;
                try {
                    switch (a.getFirst()) {
                        case "help" -> help();
                        case "tables" -> tables(tx);
                        case "use" -> use(tx, a);
                        case "rows" -> rows(tx, a);
                        case "add" -> add(tx, a);
                        case "edit" -> edit(tx, a);
                        case "delete" -> delete(tx, a);
                        case "refs", "references" -> references(tx, a);
                        case "children", "referenced-by" -> children(tx, a);
                        case "validate" -> validate(tx);
                        case "save" -> { tx.commitAndSave(); out.println("SAVE OK"); return 0; }
                        case "quit", "exit" -> { out.println("QUIT: staged changes discarded"); return 0; }
                        default -> err.println("ERROR: unknown command '" + a.getFirst() + "' (type 'help')");
                    }
                } catch (Exception e) {
                    err.println("ERROR: " + e.getMessage());
                }
            }
        }
    }

    private void help() {
        out.println("Commands:");
        out.println("  tables");
        out.println("  use <table>");
        out.println("  rows [column=value ...]");
        out.println("  add column=value ...");
        out.println("  edit column=value ... --set column=value ...");
        out.println("  delete [--cascade] column=value ...");
        out.println("  refs column=value ...");
        out.println("  children column=value ...");
        out.println("  validate");
        out.println("  save");
        out.println("  quit");
        out.println("Values containing spaces may be quoted.");
    }

    private void tables(Transaction tx) {
        QueryEngine q = new QueryEngine(tx.snapshot());
        out.println("table\trows\tprimary_key");
        for (TableInfo t : q.tables()) out.printf("%s\t%d\t%s%n", t.name(), t.rows(), String.join(",", t.primaryKeyColumns()));
    }

    private void use(Transaction tx, List<String> a) {
        if (a.size() != 2) throw new IllegalArgumentException("use requires <table>");
        tx.snapshot().schema().requireTable(a.get(1));
        table = a.get(1);
        out.println("USING " + table);
    }

    private void rows(Transaction tx, List<String> a) {
        String t = requireTable();
        Map<String,String> where = pairs(a, 1, a.size());
        List<QueryRow> rows = new QueryEngine(tx.snapshot()).select(t, where, List.of(), List.of());
        printRows(rows);
    }

    private void add(Transaction tx, List<String> a) {
        String t = requireTable();
        Map<String,String> values = pairsRequired(a, 1, a.size());
        tx.insertValidated(t, values);
        out.println("STAGED INSERT table=" + t);
    }

    private void edit(Transaction tx, List<String> a) {
        String t = requireTable();
        int set = a.indexOf("--set");
        if (set < 2 || set == a.size()-1) throw new IllegalArgumentException("edit requires match pairs followed by --set changes");
        Map<String,String> key = pairsRequired(a, 1, set);
        Map<String,String> changes = pairsRequired(a, set + 1, a.size());
        tx.updateValidated(t, key, changes);
        out.println("STAGED UPDATE table=" + t);
    }

    private void delete(Transaction tx, List<String> a) {
        String t = requireTable();
        boolean cascade = a.size() > 1 && a.get(1).equals("--cascade");
        int from = cascade ? 2 : 1;
        Map<String,String> key = pairsRequired(a, from, a.size());
        tx.deleteValidated(t, key, cascade ? DeleteRule.CASCADE : DeleteRule.RESTRICT);
        out.println("STAGED DELETE table=" + t + " rule=" + (cascade ? "CASCADE" : "RESTRICT"));
    }

    private void references(Transaction tx, List<String> a) {
        String t = requireTable();
        var rows = new QueryEngine(tx.snapshot()).references(t, pairsRequired(a,1,a.size()));
        printRelationships(rows);
    }

    private void children(Transaction tx, List<String> a) {
        String t = requireTable();
        var rows = new QueryEngine(tx.snapshot()).referencedBy(t, pairsRequired(a,1,a.size()));
        printRelationships(rows);
    }

    private void validate(Transaction tx) {
        var result = tx.validate();
        if (result.valid()) out.println("validation=OK");
        else {
            out.println("validation=FAILED violations=" + result.violations().size());
            for (var v : result.violations()) err.println(v);
        }
    }

    private String requireTable() {
        if (table == null) throw new IllegalArgumentException("select a table first with: use <table>");
        return table;
    }

    private void printRows(List<QueryRow> rows) {
        if (rows.isEmpty()) { out.println("rows=0"); return; }
        List<String> columns = new ArrayList<>(rows.getFirst().values().keySet());
        out.println(String.join("\t", columns));
        for (QueryRow row : rows) {
            List<String> values = new ArrayList<>();
            for (String c : columns) values.add(row.values().getOrDefault(c, ""));
            out.println(String.join("\t", values));
        }
        out.println("rows=" + rows.size());
    }

    private void printRelationships(List<RelationshipRow> rows) {
        if (rows.isEmpty()) { out.println("relationships=0"); return; }
        for (RelationshipRow rel : rows) {
            out.printf("[%s] %s line=%d%n", rel.relationship(), rel.row().table(), rel.row().lineNumber());
            for (var e : rel.row().values().entrySet()) out.printf("  %s=%s%n", e.getKey(), e.getValue());
        }
        out.println("relationships=" + rows.size());
    }

    private static Map<String,String> pairs(List<String> a, int from, int to) {
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        for (int i=from;i<to;i++) {
            String s=a.get(i); int p=s.indexOf('=');
            if (p<=0) throw new IllegalArgumentException("expected column=value, got: " + s);
            out.put(s.substring(0,p), s.substring(p+1));
        }
        return out;
    }

    private static Map<String,String> pairsRequired(List<String> a, int from, int to) {
        if (from>=to) throw new IllegalArgumentException("at least one column=value pair is required");
        return pairs(a,from,to);
    }

    static List<String> tokenize(String line) {
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        boolean escape = false;
        for (int i=0;i<line.length();i++) {
            char c=line.charAt(i);
            if (escape) { cur.append(c); escape=false; continue; }
            if (c=='\\') { escape=true; continue; }
            if (quote!=0) {
                if (c==quote) quote=0; else cur.append(c);
            } else if (c=='\'' || c=='\"') quote=c;
            else if (Character.isWhitespace(c)) { if (cur.length()>0) { out.add(cur.toString()); cur.setLength(0); } }
            else cur.append(c);
        }
        if (escape) cur.append('\\');
        if (quote!=0) throw new IllegalArgumentException("unterminated quote");
        if (cur.length()>0) out.add(cur.toString());
        return List.copyOf(out);
    }
}
