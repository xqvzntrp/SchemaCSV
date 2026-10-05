package io.schemacsv.schema;

public record CheckConstraintSchema(String name, String column, String operator, String operand) {}
