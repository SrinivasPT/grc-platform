package com.grcplatform.core.rule;

/**
 * Evaluates TRIGGER rules — returns whether the post-save side-effect event should fire. Pure and
 * side-effect-free. The actual event dispatch is the service layer's responsibility.
 */
public class TriggerRuleEvaluator extends BaseRuleEvaluator implements RuleEvaluator {

    @Override
    public RuleContext context() {
        return RuleContext.TRIGGER;
    }

    @Override
    public EvaluationResult evaluate(RuleNode rule, EvaluationInput input) {
        boolean triggered = evaluateBoolean(rule, input);
        return triggered ? EvaluationResult.TriggerResult.yes()
                : EvaluationResult.TriggerResult.no();
    }
}
