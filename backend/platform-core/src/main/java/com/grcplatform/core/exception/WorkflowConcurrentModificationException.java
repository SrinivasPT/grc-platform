package com.grcplatform.core.exception;

public class WorkflowConcurrentModificationException extends RuntimeException {
    public WorkflowConcurrentModificationException(java.util.UUID instanceId) {
        super("Concurrent modification on workflow instance: " + instanceId);
    }
}
