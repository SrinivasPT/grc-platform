package com.grcplatform.core.rule;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class ComputeRuleEvaluatorTest {

    private RuleDslParser parser;
    private ComputeRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        parser = new RuleDslParser(objectMapper);
        evaluator = new ComputeRuleEvaluator();
    }

    @Test
    void context_returnsCompute() {
        assertThat(evaluator.context()).isEqualTo(RuleContext.COMPUTE);
    }

    @Test
    void evaluate_computesMultiplication_fromFieldValues() {
        String json = """
                {"arithmetic":{"op":"*","operands":[{"field":"likelihood"},{"field":"impact"}]}}""";
        RuleNode rule = parser.parse(json);
        EvaluationInput input = EvaluationInput.of(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Map.of("likelihood", 3.0, "impact", 4.0), Map.of());

        EvaluationResult result = evaluator.evaluate(rule, input);

        assertThat(result).isInstanceOf(EvaluationResult.ComputeResult.class);
        Number value = (Number) ((EvaluationResult.ComputeResult) result).value();
        assertThat(value.doubleValue()).isEqualTo(12.0);
    }

    @Test
    void evaluate_evaluatesConditionalExpression_whenConditionTrue() {
        String json = """
                {"if":{
                  "condition":{"compare":{"field":"riskLevel","op":"EQ","value":"HIGH"}},
                  "then":{"value":10},
                  "else":{"value":5}
                }}""";
        RuleNode rule = parser.parse(json);
        EvaluationInput input = EvaluationInput.of(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Map.of("riskLevel", "HIGH"), Map.of());

        EvaluationResult result = evaluator.evaluate(rule, input);

        assertThat(((EvaluationResult.ComputeResult) result).value()).isEqualTo(10);
    }

    @Test
    void evaluate_evaluatesConditionalExpression_whenConditionFalse() {
        String json = """
                {"if":{
                  "condition":{"compare":{"field":"riskLevel","op":"EQ","value":"HIGH"}},
                  "then":{"value":10},
                  "else":{"value":5}
                }}""";
        RuleNode rule = parser.parse(json);
        EvaluationInput input = EvaluationInput.of(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Map.of("riskLevel", "LOW"), Map.of());

        EvaluationResult result = evaluator.evaluate(rule, input);

        assertThat(((EvaluationResult.ComputeResult) result).value()).isEqualTo(5);
    }

    @Test
    void evaluate_returnsNullComputeResult_whenFieldRefNotFound() {
        String json = "{\"field\":\"nonExistentField\"}";
        RuleNode rule = parser.parse(json);
        EvaluationInput input = EvaluationInput.of(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Map.of(), Map.of());

        EvaluationResult result = evaluator.evaluate(rule, input);

        assertThat(result).isInstanceOf(EvaluationResult.ComputeResult.class);
        assertThat(((EvaluationResult.ComputeResult) result).value()).isNull();
    }

    @Test
    void evaluate_computesAddition() {
        String json = """
                {"arithmetic":{"op":"+","operands":[{"field":"a"},{"field":"b"}]}}""";
        RuleNode rule = parser.parse(json);
        EvaluationInput input = EvaluationInput.of(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Map.of("a", 7.0, "b", 3.0), Map.of());

        EvaluationResult result = evaluator.evaluate(rule, input);

        Number value = (Number) ((EvaluationResult.ComputeResult) result).value();
        assertThat(value.doubleValue()).isEqualTo(10.0);
    }
}
