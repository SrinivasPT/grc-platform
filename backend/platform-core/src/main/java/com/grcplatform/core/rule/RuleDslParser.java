package com.grcplatform.core.rule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grcplatform.core.exception.RuleCountExceededException;
import com.grcplatform.core.exception.RuleDepthExceededException;
import com.grcplatform.core.exception.RuleParseException;

import java.util.ArrayList;
import java.util.List;

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
        if (json == null) {
            throw new RuleParseException("Rule DSL JSON must not be null");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            int[] ruleCount = {0};
            return parseNode(root, 1, ruleCount);
        } catch (JsonProcessingException e) {
            throw new RuleParseException("Invalid rule DSL JSON: " + e.getMessage());
        }
    }

    private RuleNode parseNode(JsonNode node, int depth, int[] ruleCount) {
        if (depth > MAX_DEPTH) {
            throw new RuleDepthExceededException(depth, MAX_DEPTH);
        }
        ruleCount[0]++;
        if (ruleCount[0] > MAX_RULES) {
            throw new RuleCountExceededException(ruleCount[0], MAX_RULES);
        }

        if (node.has("and")) {
            List<RuleNode> operands = parseArray(node.get("and"), depth + 1, ruleCount);
            return new RuleNode.AndNode(operands);
        }
        if (node.has("or")) {
            List<RuleNode> operands = parseArray(node.get("or"), depth + 1, ruleCount);
            return new RuleNode.OrNode(operands);
        }
        if (node.has("not")) {
            return new RuleNode.NotNode(parseNode(node.get("not"), depth + 1, ruleCount));
        }
        if (node.has("compare")) {
            return parseCompareNode(node.get("compare"), depth, ruleCount);
        }
        if (node.has("field")) {
            return new RuleNode.FieldRefNode(node.get("field").asText());
        }
        if (node.has("value")) {
            return new RuleNode.LiteralNode(parseScalarValue(node.get("value")));
        }
        if (node.has("fn")) {
            JsonNode fn = node.get("fn");
            String name = fn.get("name").asText();
            List<RuleNode> args =
                    fn.has("args") ? parseArray(fn.get("args"), depth + 1, ruleCount) : List.of();
            return new RuleNode.FunctionCallNode(name, args);
        }
        if (node.has("arithmetic")) {
            JsonNode arith = node.get("arithmetic");
            String op = arith.get("op").asText();
            List<RuleNode> operands = parseArray(arith.get("operands"), depth + 1, ruleCount);
            return new RuleNode.ArithmeticNode(op, operands);
        }
        if (node.has("if")) {
            JsonNode ifNode = node.get("if");
            RuleNode condition = parseNode(ifNode.get("condition"), depth + 1, ruleCount);
            RuleNode thenExpr = parseNode(ifNode.get("then"), depth + 1, ruleCount);
            RuleNode elseExpr = parseNode(ifNode.get("else"), depth + 1, ruleCount);
            return new RuleNode.IfNode(condition, thenExpr, elseExpr);
        }
        if (node.has("aggregate")) {
            return parseAggregateNode(node.get("aggregate"), depth, ruleCount);
        }
        if (node.has("lookup")) {
            return parseLookupNode(node.get("lookup"), depth, ruleCount);
        }

        String unknownKey = node.fieldNames().hasNext() ? node.fieldNames().next() : "<empty>";
        throw new RuleParseException("Unknown rule node type: " + unknownKey);
    }

    private RuleNode.CompareNode parseCompareNode(JsonNode compare, int depth, int[] ruleCount) {
        String field = compare.get("field").asText();
        String opStr = compare.get("op").asText();
        RuleNode.CompareOp op;
        try {
            op = RuleNode.CompareOp.valueOf(opStr);
        } catch (IllegalArgumentException e) {
            throw new RuleParseException("Unknown compare operator: " + opStr);
        }
        Object value = parseScalarValue(compare.get("value"));
        return new RuleNode.CompareNode(new RuleNode.FieldRefNode(field), op,
                new RuleNode.LiteralNode(value));
    }

    private RuleNode.AggregateNode parseAggregateNode(JsonNode agg, int depth, int[] ruleCount) {
        String source = agg.get("source").asText();
        String relationType = agg.get("relationType").asText();
        String direction = agg.get("direction").asText();
        RuleNode filter =
                agg.has("filter") ? parseNode(agg.get("filter"), depth + 1, ruleCount) : null;
        String function = agg.get("function").asText();
        String field = agg.get("field").asText();
        return new RuleNode.AggregateNode(source, relationType, direction, filter, function, field);
    }

    private RuleNode.LookupNode parseLookupNode(JsonNode lookup, int depth, int[] ruleCount) {
        String source = lookup.get("source").asText();
        String listKey = lookup.get("listKey").asText();
        String matchField = lookup.get("matchField").asText();
        RuleNode matchValue = parseNode(lookup.get("matchValue"), depth + 1, ruleCount);
        String returnField = lookup.get("returnField").asText();
        return new RuleNode.LookupNode(source, listKey, matchField, matchValue, returnField);
    }

    private List<RuleNode> parseArray(JsonNode arrayNode, int depth, int[] ruleCount) {
        List<RuleNode> result = new ArrayList<>();
        for (JsonNode element : arrayNode) {
            result.add(parseNode(element, depth, ruleCount));
        }
        return result;
    }

    private Object parseScalarValue(JsonNode value) {
        if (value == null || value.isNull())
            return null;
        if (value.isBoolean())
            return value.asBoolean();
        if (value.isInt())
            return value.asInt();
        if (value.isLong())
            return value.asLong();
        if (value.isDouble() || value.isFloat())
            return value.asDouble();
        if (value.isNumber())
            return value.numberValue();
        if (value.isTextual())
            return value.asText();
        if (value.isArray()) {
            List<Object> items = new ArrayList<>();
            for (JsonNode item : value) {
                items.add(parseScalarValue(item));
            }
            return items;
        }
        return value.asText();
    }
}
