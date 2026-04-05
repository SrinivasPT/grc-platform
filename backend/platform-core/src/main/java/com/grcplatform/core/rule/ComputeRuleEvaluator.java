package com.grcplatform.core.rule;

/**
 * Evaluates COMPUTE rules — returns the derived value for a target field. Pure and
 * side-effect-free.
 */
public class ComputeRuleEvaluator extends BaseRuleEvaluator implements RuleEvaluator {

    @Override
    public RuleContext context() {
        return RuleContext.COMPUTE;
    }

    @Override
    public EvaluationResult evaluate(RuleNode rule, EvaluationInput input) {
        Object value = resolveValue(rule, input);
        return new EvaluationResult.ComputeResult(value);
    }
}
