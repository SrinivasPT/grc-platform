package com.grcplatform.core.exception;

public class RuleDepthExceededException extends RuntimeException {

    public RuleDepthExceededException(int depth, int maxDepth) {
        super("Rule nesting depth " + depth + " exceeds the maximum allowed depth of " + maxDepth);
    }
}
