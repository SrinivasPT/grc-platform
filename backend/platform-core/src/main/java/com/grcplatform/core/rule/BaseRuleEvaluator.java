package com.grcplatform.core.rule;

import com.grcplatform.core.exception.RuleEvaluationException;

import java.util.Collection;
import java.util.List;

/**
 * Shared boolean and numeric evaluation logic for all three rule evaluators. Keeps each evaluator's
 * cognitive complexity under the limit of 10.
 */
abstract class BaseRuleEvaluator {

    /**
     * Evaluates a node to a raw Object value (used by ComputeRuleEvaluator).
     */
    protected Object resolveValue(RuleNode node, EvaluationInput input) {
        return switch (node) {
            case RuleNode.FieldRefNode ref -> input.currentFieldValues().get(ref.fieldKey());
            case RuleNode.LiteralNode lit -> lit.value();
            case RuleNode.ArithmeticNode arith -> evaluateArithmetic(arith, input);
            case RuleNode.IfNode ifNode -> resolveIf(ifNode, input);
            case RuleNode.FunctionCallNode fn -> throw new RuleEvaluationException(
                    "Function '" + fn.functionName() + "' is not yet supported in this evaluator");
            case RuleNode.AggregateNode agg -> throw new RuleEvaluationException(
                    "Aggregate nodes require relation data and cannot be evaluated here");
            case RuleNode.LookupNode lu -> throw new RuleEvaluationException(
                    "Lookup nodes require value list data and cannot be evaluated here");
            default -> evaluateBoolean(node, input);
        };
    }

    /**
     * Evaluates a node as a boolean condition (used by ValidateRuleEvaluator and
     * TriggerRuleEvaluator).
     */
    protected boolean evaluateBoolean(RuleNode node, EvaluationInput input) {
        return switch (node) {
            case RuleNode.AndNode and -> evaluateAnd(and, input);
            case RuleNode.OrNode or -> evaluateOr(or, input);
            case RuleNode.NotNode not -> !evaluateBoolean(not.operand(), input);
            case RuleNode.CompareNode cmp -> evaluateCompare(cmp, input);
            case RuleNode.IfNode ifNode -> (Boolean) resolveIf(ifNode, input);
            default -> throw new RuleEvaluationException(
                    "Cannot evaluate node type as boolean: " + node.getClass().getSimpleName());
        };
    }

    private boolean evaluateAnd(RuleNode.AndNode and, EvaluationInput input) {
        for (RuleNode operand : and.operands()) {
            if (!evaluateBoolean(operand, input))
                return false;
        }
        return true;
    }

    private boolean evaluateOr(RuleNode.OrNode or, EvaluationInput input) {
        for (RuleNode operand : or.operands()) {
            if (evaluateBoolean(operand, input))
                return true;
        }
        return false;
    }

    private boolean evaluateCompare(RuleNode.CompareNode cmp, EvaluationInput input) {
        Object left = input.currentFieldValues().get(cmp.left().fieldKey());
        Object right = cmp.right().value();
        return applyOp(left, cmp.op(), right);
    }

    @SuppressWarnings("unchecked")
    private boolean applyOp(Object left, RuleNode.CompareOp op, Object right) {
        return switch (op) {
            case EQ -> equalValues(left, right);
            case NEQ -> !equalValues(left, right);
            case GT -> compareNumbers(left, right) > 0;
            case GTE -> compareNumbers(left, right) >= 0;
            case LT -> compareNumbers(left, right) < 0;
            case LTE -> compareNumbers(left, right) <= 0;
            case CONTAINS -> left instanceof String s && s.contains(right.toString());
            case STARTS_WITH -> left instanceof String s && s.startsWith(right.toString());
            case IN -> right instanceof Collection<?> col ? col.contains(left)
                    : right instanceof List<?> list && list.contains(left);
        };
    }

    private boolean equalValues(Object left, Object right) {
        if (left == null && right == null)
            return true;
        if (left == null || right == null)
            return false;
        if (left instanceof Number l && right instanceof Number r) {
            return Double.compare(l.doubleValue(), r.doubleValue()) == 0;
        }
        return left.equals(right);
    }

    private int compareNumbers(Object left, Object right) {
        if (left instanceof Number l && right instanceof Number r) {
            return Double.compare(l.doubleValue(), r.doubleValue());
        }
        throw new RuleEvaluationException("Numeric comparison requires numeric operands, got: "
                + (left == null ? "null" : left.getClass().getSimpleName()) + " and "
                + (right == null ? "null" : right.getClass().getSimpleName()));
    }

    private Object evaluateArithmetic(RuleNode.ArithmeticNode arith, EvaluationInput input) {
        List<Double> operands =
                arith.operands().stream().map(op -> toDouble(resolveValue(op, input))).toList();

        return switch (arith.op()) {
            case "+" -> operands.stream().mapToDouble(Double::doubleValue).sum();
            case "-" -> operands.get(0)
                    - operands.stream().skip(1).mapToDouble(Double::doubleValue).sum();
            case "*" -> operands.stream().mapToDouble(Double::doubleValue).reduce(1.0,
                    (a, b) -> a * b);
            case "/" -> {
                if (operands.size() != 2)
                    throw new RuleEvaluationException("Division requires exactly 2 operands");
                if (operands.get(1) == 0.0)
                    throw new RuleEvaluationException("Division by zero");
                yield operands.get(0) / operands.get(1);
            }
            default -> throw new RuleEvaluationException(
                    "Unknown arithmetic operator: " + arith.op());
        };
    }

    private Object resolveIf(RuleNode.IfNode ifNode, EvaluationInput input) {
        boolean condition = evaluateBoolean(ifNode.condition(), input);
        return condition ? resolveValue(ifNode.thenExpr(), input)
                : resolveValue(ifNode.elseExpr(), input);
    }

    private double toDouble(Object value) {
        if (value instanceof Number n)
            return n.doubleValue();
        throw new RuleEvaluationException("Expected numeric value, got: "
                + (value == null ? "null" : value.getClass().getSimpleName()));
    }
}
