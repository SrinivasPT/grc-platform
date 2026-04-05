package com.grcplatform.core.rule;

/**
 * Sealed AST hierarchy for the GRC Rule DSL.
 *
 * IMPORTANT: Never deserialize these types via raw ObjectMapper with @JsonTypeInfo. Use
 * RuleDslParser exclusively — see ADR-006.
 */
public sealed interface RuleNode
        permits RuleNode.AndNode, RuleNode.OrNode, RuleNode.NotNode, RuleNode.CompareNode,
        RuleNode.FieldRefNode, RuleNode.LiteralNode, RuleNode.FunctionCallNode,
        RuleNode.ArithmeticNode, RuleNode.IfNode, RuleNode.AggregateNode, RuleNode.LookupNode {

    record AndNode(java.util.List<RuleNode> operands) implements RuleNode {
        public AndNode {
            operands = java.util.List.copyOf(operands);
        }
    }

    record OrNode(java.util.List<RuleNode> operands) implements RuleNode {
        public OrNode {
            operands = java.util.List.copyOf(operands);
        }
    }

    record NotNode(RuleNode operand) implements RuleNode {
    }

    record CompareNode(FieldRefNode left, CompareOp op, LiteralNode right) implements RuleNode {
    }

    record FieldRefNode(String fieldKey) implements RuleNode {
    }

    record LiteralNode(Object value) implements RuleNode {
    }

    record FunctionCallNode(String functionName, java.util.List<RuleNode> args)
            implements RuleNode {
        public FunctionCallNode {
            args = java.util.List.copyOf(args);
        }
    }

    record ArithmeticNode(String op, java.util.List<RuleNode> operands) implements RuleNode {
        public ArithmeticNode {
            operands = java.util.List.copyOf(operands);
        }
    }

    record IfNode(RuleNode condition, RuleNode thenExpr, RuleNode elseExpr) implements RuleNode {
    }

    record AggregateNode(String source, String relationType, String direction, RuleNode filter,
            String function, String field) implements RuleNode {
    }

    record LookupNode(String source, String listKey, String matchField, RuleNode matchValue,
            String returnField) implements RuleNode {
    }

    enum CompareOp {
        EQ, NEQ, LT, LTE, GT, GTE, CONTAINS, STARTS_WITH, IN
    }
}
