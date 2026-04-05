package com.grcplatform.core.exception;

public class OptimisticLockConflictException extends RuntimeException {

    public OptimisticLockConflictException(String entityType, Object id) {
        super("Optimistic lock conflict on " + entityType + " with id=" + id
                + ". Please reload and retry.");
    }

    public OptimisticLockConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
