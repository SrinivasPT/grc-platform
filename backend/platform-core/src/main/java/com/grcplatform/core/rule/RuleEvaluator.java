package com.grcplatform.core.rule;

/**
 * Contract for all rule evaluators. Implementations must be pure and side-effect-free — never write
 * to the database.
 */
public interface RuleEvaluator {

    /**
     * Returns the execution context this evaluator handles.
     */
    RuleContext context();

    /**
     * Evaluates the given rule node against the provided input.
     *
     * @param rule the parsed rule AST
     * @param input the current record field values snapshot
     * @return the evaluation result appropriate for this evaluator's context
     */
    EvaluationResult evaluate(RuleNode rule, EvaluationInput input);
}
