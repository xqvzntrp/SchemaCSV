package io.schemacsv.validation;

import java.util.List;

public record ValidationResult(List<ConstraintViolation> violations) {
    public ValidationResult { violations=List.copyOf(violations); }
    public boolean valid(){return violations.isEmpty();}
}
