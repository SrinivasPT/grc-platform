package com.grcplatform.core.exception;

public class RuleCountExceededException extends RuntimeException {

    public RuleCountExceededException(int count, int maxCount) {
        super("Rule count " + count + " exceeds the maximum allowed rules per save (" + maxCount
                + ")");
    }
}
