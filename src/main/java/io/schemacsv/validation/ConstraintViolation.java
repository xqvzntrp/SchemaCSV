package io.schemacsv.validation;

import java.nio.file.Path;

public record ConstraintViolation(String code, String message, Path file, int line) {
    @Override public String toString() {
        String where = file == null ? "" : file + (line > 0 ? ":" + line : "") + ": ";
        return where + code + ": " + message;
    }
}
