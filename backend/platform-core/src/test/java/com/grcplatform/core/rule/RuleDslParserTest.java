package com.grcplatform.core.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.grcplatform.core.exception.RuleDepthExceededException;
import com.grcplatform.core.exception.RuleParseException;

class RuleDslParserTest {

    private RuleDslParser parser;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        parser = new RuleDslParser(objectMapper);
    }

    @Test
    void parse_parsesAndNode() {
        String json = """
                {"and":[
                  {"compare":{"field":"status","op":"EQ","value":"active"}},
                  {"compare":{"field":"riskLevel","op":"GT","value":2}}
                ]}""";

        RuleNode result = parser.parse(json);

        assertThat(result).isInstanceOf(RuleNode.AndNode.class);
        RuleNode.AndNode and = (RuleNode.AndNode) result;
        assertThat(and.operands()).hasSize(2);
    }

    @Test
    void parse_parsesCompareNode() {
        String json = """
                {"compare":{"field":"severity","op":"EQ","value":"HIGH"}}""";

        RuleNode result = parser.parse(json);

        assertThat(result).isInstanceOf(RuleNode.CompareNode.class);
        RuleNode.CompareNode compare = (RuleNode.CompareNode) result;
        assertThat(compare.left().fieldKey()).isEqualTo("severity");
        assertThat(compare.op()).isEqualTo(RuleNode.CompareOp.EQ);
        assertThat(compare.right().value()).isEqualTo("HIGH");
    }

    @Test
    void parse_parsesArithmeticExpression() {
        String json = """
                {"arithmetic":{"op":"*","operands":[{"field":"likelihood"},{"field":"impact"}]}}""";

        RuleNode result = parser.parse(json);

        assertThat(result).isInstanceOf(RuleNode.ArithmeticNode.class);
        RuleNode.ArithmeticNode arith = (RuleNode.ArithmeticNode) result;
        assertThat(arith.op()).isEqualTo("*");
        assertThat(arith.operands()).hasSize(2);
        assertThat(((RuleNode.FieldRefNode) arith.operands().get(0)).fieldKey())
                .isEqualTo("likelihood");
    }

    @Test
    void parse_parsesConditionalExpression() {
        String json = """
                {"if":{
                  "condition":{"compare":{"field":"riskLevel","op":"EQ","value":"HIGH"}},
                  "then":{"value":10},
                  "else":{"value":5}
                }}""";

        RuleNode result = parser.parse(json);

        assertThat(result).isInstanceOf(RuleNode.IfNode.class);
        RuleNode.IfNode ifNode = (RuleNode.IfNode) result;
        assertThat(ifNode.thenExpr()).isInstanceOf(RuleNode.LiteralNode.class);
        assertThat(((RuleNode.LiteralNode) ifNode.thenExpr()).value()).isEqualTo(10);
        assertThat(((RuleNode.LiteralNode) ifNode.elseExpr()).value()).isEqualTo(5);
    }

    @Test
    void parse_throwsOnDepthExceeding5() {
        // Nest 6 levels deep: and(and(and(and(and(compare)))))
        String json = """
                {"and":[{"and":[{"and":[{"and":[{"and":[
                  {"compare":{"field":"f","op":"EQ","value":"v"}}
                ]}]}]}]}]}""";

        assertThatThrownBy(() -> parser.parse(json)).isInstanceOf(RuleDepthExceededException.class);
    }

    @Test
    void parse_throwsOnUnknownNodeKey() {
        String json = """
                {"unknownNodeType":{"field":"status"}}""";

        assertThatThrownBy(() -> parser.parse(json)).isInstanceOf(RuleParseException.class)
                .hasMessageContaining("Unknown rule node type");
    }

    @Test
    void parse_throwsOnNullInput() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(RuleParseException.class);
    }

    @Test
    void parse_throwsOnInvalidJson() {
        assertThatThrownBy(() -> parser.parse("{not valid json"))
                .isInstanceOf(RuleParseException.class);
    }

    @Test
    void parse_parsesOrNode() {
        String json = """
                {"or":[{"field":"a"},{"field":"b"}]}""";

        RuleNode result = parser.parse(json);

        assertThat(result).isInstanceOf(RuleNode.OrNode.class);
    }

    @Test
    void parse_parsesNotNode() {
        String json = """
                {"not":{"compare":{"field":"deleted","op":"EQ","value":true}}}""";

        RuleNode result = parser.parse(json);

        assertThat(result).isInstanceOf(RuleNode.NotNode.class);
    }

    @Test
    void parse_parsesLiteralIntValue() {
        String json = "{\"value\":42}";

        RuleNode result = parser.parse(json);

        assertThat(result).isInstanceOf(RuleNode.LiteralNode.class);
        assertThat(((RuleNode.LiteralNode) result).value()).isEqualTo(42);
    }

    @Test
    void parse_parsesFieldRefNode() {
        String json = "{\"field\":\"riskScore\"}";

        RuleNode result = parser.parse(json);

        assertThat(result).isInstanceOf(RuleNode.FieldRefNode.class);
        assertThat(((RuleNode.FieldRefNode) result).fieldKey()).isEqualTo("riskScore");
    }

    @Test
    void parse_parsesAggregateNode() {
        String json = """
                {"aggregate":{
                  "source":"linked",
                  "relationType":"mitigates",
                  "direction":"IN",
                  "filter":{"compare":{"field":"status","op":"EQ","value":"active"}},
                  "function":"SUM",
                  "field":"controlScore"
                }}""";

        RuleNode result = parser.parse(json);

        assertThat(result).isInstanceOf(RuleNode.AggregateNode.class);
        RuleNode.AggregateNode agg = (RuleNode.AggregateNode) result;
        assertThat(agg.function()).isEqualTo("SUM");
        assertThat(agg.relationType()).isEqualTo("mitigates");
        assertThat(agg.filter()).isInstanceOf(RuleNode.CompareNode.class);
    }
}
