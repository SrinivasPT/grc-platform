package com.grcplatform.core.rule;

import java.util.List;
import java.util.stream.StreamSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grcplatform.core.exception.RuleCountExceededException;
import com.grcplatform.core.exception.RuleDepthExceededException;
import com.grcplatform.core.exception.RuleParseException;

/**
 * Parses rule DSL JSON into the sealed RuleNode AST.
 *
 * NEVER use @JsonTypeInfo or raw ObjectMapper.readValue(json, RuleNode.class). This class manually
 * dispatches by JSON field key — see ADR-006.
 */
public class RuleDslParser {

    private static final int MAX_DEPTH = 5;
    private static final int MAX_RULES = 50;

    private final ObjectMapper objectMapper;

    public RuleDslParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RuleNode parse(String json) {
        if (json == null) throw new RuleParseException("Rule DSL JSON must not be null");
        try {
            return parseNode(objectMapper.readTree(json), 1, new int[] {0});
        } catch (JsonProcessingException e) {
            throw new RuleParseException("Invalid rule DSL JSON: " + e.getMessage());
        }
    }

    private RuleNode parseNode(JsonNode node, int depth, int[] ruleCount) {
        if (depth > MAX_DEPTH) throw new RuleDepthExceededException(depth, MAX_DEPTH);
        if (++ruleCount[0] > MAX_RULES)
            throw new RuleCountExceededException(ruleCount[0], MAX_RULES);

        var key = node.fieldNames().hasNext() ? node.fieldNames().next() : "<empty>";
        return switch (key) {
            case "and" -> new RuleNode.AndNode(parseArray(node.get("and"), depth + 1, ruleCount));
            case "or" -> new RuleNode.OrNode(parseArray(node.get("or"), depth + 1, ruleCount));
            case "not" -> new RuleNode.NotNode(parseNode(node.get("not"), depth + 1, ruleCount));
            case "compare" -> parseCompareNode(node.get("compare"), depth, ruleCount);
            case "field" -> new RuleNode.FieldRefNode(node.get("field").asText());
            case "value" -> new RuleNode.LiteralNode(parseScalarValue(node.get("value")));
            case "fn" -> parseFnNode(node.get("fn"), depth, ruleCount);
            case "arithmetic" -> parseArithmeticNode(node.get("arithmetic"), depth, ruleCount);
            case "if" -> parseIfNode(node.get("if"), depth, ruleCount);
            case "aggregate" -> parseAggregateNode(node.get("aggregate"), depth, ruleCount);
            case "lookup" -> parseLookupNode(node.get("lookup"), depth, ruleCount);
            default -> throw new RuleParseException("Unknown rule node type: " + key);
        };
    }

    private RuleNode.CompareNode parseCompareNode(JsonNode compare, int depth, int[] ruleCount) {
        var field = compare.get("field").asText();
        var opStr = compare.get("op").asText();
        try {
            var op = RuleNode.CompareOp.valueOf(opStr);
            var value = parseScalarValue(compare.get("value"));
            return new RuleNode.CompareNode(new RuleNode.FieldRefNode(field), op,
                    new RuleNode.LiteralNode(value));
        } catch (IllegalArgumentException e) {
            throw new RuleParseException("Unknown compare operator: " + opStr);
        }
    }

    private RuleNode.FunctionCallNode parseFnNode(JsonNode fn, int depth, int[] ruleCount) {
        var name = fn.get("name").asText();
        var args = fn.has("args") ? parseArray(fn.get("args"), depth + 1, ruleCount)
                : List.<RuleNode>of();
        return new RuleNode.FunctionCallNode(name, args);
    }

    private RuleNode.ArithmeticNode parseArithmeticNode(JsonNode arith, int depth,
            int[] ruleCount) {
        return new RuleNode.ArithmeticNode(arith.get("op").asText(),
                parseArray(arith.get("operands"), depth + 1, ruleCount));
    }

    private RuleNode.IfNode parseIfNode(JsonNode ifNode, int depth, int[] ruleCount) {
        return new RuleNode.IfNode(parseNode(ifNode.get("condition"), depth + 1, ruleCount),
                parseNode(ifNode.get("then"), depth + 1, ruleCount),
                parseNode(ifNode.get("else"), depth + 1, ruleCount));
    }

    private RuleNode.AggregateNode parseAggregateNode(JsonNode agg, int depth, int[] ruleCount) {
        var filter = agg.has("filter") ? parseNode(agg.get("filter"), depth + 1, ruleCount) : null;
        return new RuleNode.AggregateNode(agg.get("source").asText(),
                agg.get("relationType").asText(), agg.get("direction").asText(), filter,
                agg.get("function").asText(), agg.get("field").asText());
    }

    private RuleNode.LookupNode parseLookupNode(JsonNode lookup, int depth, int[] ruleCount) {
        return new RuleNode.LookupNode(lookup.get("source").asText(),
                lookup.get("listKey").asText(), lookup.get("matchField").asText(),
                parseNode(lookup.get("matchValue"), depth + 1, ruleCount),
                lookup.get("returnField").asText());
    }

    private List<RuleNode> parseArray(JsonNode arrayNode, int depth, int[] ruleCount) {
        return StreamSupport.stream(arrayNode.spliterator(), false)
                .map(el -> parseNode(el, depth, ruleCount)).toList();
    }

    private Object parseScalarValue(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isBoolean()) return value.asBoolean();
        if (value.isInt()) return value.asInt();
        if (value.isLong()) return value.asLong();
        if (value.isDouble() || value.isFloat()) return value.asDouble();
        if (value.isNumber()) return value.numberValue();
        if (value.isArray()) return StreamSupport.stream(value.spliterator(), false)
                .map(this::parseScalarValue).toList();
        return value.asText();
    }
}
