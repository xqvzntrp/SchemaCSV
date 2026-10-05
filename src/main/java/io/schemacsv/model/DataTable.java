package io.schemacsv.model;

import io.schemacsv.csv.CsvTable;
import io.schemacsv.schema.TableSchema;

public record DataTable(TableSchema schema, CsvTable csv) {}
