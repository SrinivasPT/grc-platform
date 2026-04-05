package com.grcplatform.core.exception;

import java.util.UUID;

public class RecordNotFoundException extends RuntimeException {

    public RecordNotFoundException(UUID recordId) {
        super("Record not found: " + recordId);
    }

    public RecordNotFoundException(String entityType, UUID id) {
        super(entityType + " not found: " + id);
    }
}
