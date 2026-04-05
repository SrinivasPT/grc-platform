package com.grcplatform.core.rule;

/**
 * Evaluates VALIDATE rules — returns whether the record satisfies the constraint. Pure and
 * side-effect-free.
 */
public class ValidateRuleEvaluator extends BaseRuleEvaluator implements RuleEvaluator {

    @Override
    public RuleContext context() {
        return RuleContext.VALIDATE;
    }

    @Override
    public EvaluationResult evaluate(RuleNode rule, EvaluationInput input) {
        var valid = evaluateBoolean(rule, input);
        return valid ? EvaluationResult.ValidateResult.pass()
                : EvaluationResult.ValidateResult.fail(null, "Validation rule failed");
    }
}
