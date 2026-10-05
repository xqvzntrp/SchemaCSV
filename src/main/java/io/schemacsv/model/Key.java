package io.schemacsv.model;

import java.util.List;

public record Key(List<String> parts) {
    public Key { parts = List.copyOf(parts); }
    @Override public String toString() { return "(" + String.join(", ", parts) + ")"; }
}
