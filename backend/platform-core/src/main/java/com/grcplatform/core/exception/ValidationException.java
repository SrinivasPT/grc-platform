package com.grcplatform.core.exception;

import java.util.List;

public class ValidationException extends RuntimeException {

    private final List<ValidationError> errors;

    public ValidationException(List<ValidationError> errors) {
        super("Validation failed: " + errors.stream().map(e -> e.fieldKey() + " — " + e.message())
                .reduce((a, b) -> a + "; " + b).orElse("no details"));
        this.errors = List.copyOf(errors);
    }

    public ValidationException(String fieldKey, String message) {
        this(List.of(new ValidationError(fieldKey, message)));
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public record ValidationError(String fieldKey, String message) {
    }
}
