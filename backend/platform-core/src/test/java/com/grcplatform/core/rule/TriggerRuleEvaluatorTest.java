package com.grcplatform.core.rule;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class TriggerRuleEvaluatorTest {

    private RuleDslParser parser;
    private TriggerRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        parser = new RuleDslParser(objectMapper);
        evaluator = new TriggerRuleEvaluator();
    }

    @Test
    void context_returnsTrigger() {
        assertThat(evaluator.context()).isEqualTo(RuleContext.TRIGGER);
    }

    @Test
    void evaluate_returnsTriggered_whenConditionMatches() {
        String json = """
                {"compare":{"field":"riskScore","op":"GTE","value":8}}""";
        RuleNode rule = parser.parse(json);
        EvaluationInput input = EvaluationInput.of(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Map.of("riskScore", 9), Map.of());

        EvaluationResult result = evaluator.evaluate(rule, input);

        assertThat(result).isInstanceOf(EvaluationResult.TriggerResult.class);
        assertThat(((EvaluationResult.TriggerResult) result).triggered()).isTrue();
    }

    @Test
    void evaluate_returnsNotTriggered_whenConditionDoesNotMatch() {
        String json = """
                {"compare":{"field":"riskScore","op":"GTE","value":8}}""";
        RuleNode rule = parser.parse(json);
        EvaluationInput input = EvaluationInput.of(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Map.of("riskScore", 3), Map.of());

        EvaluationResult result = evaluator.evaluate(rule, input);

        assertThat(result).isInstanceOf(EvaluationResult.TriggerResult.class);
        assertThat(((EvaluationResult.TriggerResult) result).triggered()).isFalse();
    }

    @Test
    void evaluate_returnsNotTriggered_whenAndConditionPartiallyFails() {
        String json = """
                {"and":[
                  {"compare":{"field":"status","op":"EQ","value":"active"}},
                  {"compare":{"field":"riskScore","op":"GT","value":7}}
                ]}""";
        RuleNode rule = parser.parse(json);
        EvaluationInput input = EvaluationInput.of(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Map.of("status", "closed", "riskScore", 9), Map.of());

        EvaluationResult result = evaluator.evaluate(rule, input);

        assertThat(((EvaluationResult.TriggerResult) result).triggered()).isFalse();
    }

    @Test
    void evaluate_returnsTriggered_whenOrConditionAnyMatches() {
        String json = """
                {"or":[
                  {"compare":{"field":"severity","op":"EQ","value":"CRITICAL"}},
                  {"compare":{"field":"riskScore","op":"GT","value":9}}
                ]}""";
        RuleNode rule = parser.parse(json);
        EvaluationInput input = EvaluationInput.of(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Map.of("severity", "CRITICAL", "riskScore", 5), Map.of());

        EvaluationResult result = evaluator.evaluate(rule, input);

        assertThat(((EvaluationResult.TriggerResult) result).triggered()).isTrue();
    }
}
