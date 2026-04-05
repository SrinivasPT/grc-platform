package com.grcplatform.core.rule;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class ValidateRuleEvaluatorTest {

    private RuleDslParser parser;
    private ValidateRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        parser = new RuleDslParser(objectMapper);
        evaluator = new ValidateRuleEvaluator();
    }

    @Test
    void context_returnsValidate() {
        assertThat(evaluator.context()).isEqualTo(RuleContext.VALIDATE);
    }

    @Test
    void evaluate_returnsValid_whenEqCompareMatches() {
        String json = """
                {"compare":{"field":"status","op":"EQ","value":"active"}}""";
        RuleNode rule = parser.parse(json);
        EvaluationInput input = EvaluationInput.of(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Map.of("status", "active"), Map.of());

        EvaluationResult result = evaluator.evaluate(rule, input);

        assertThat(result).isInstanceOf(EvaluationResult.ValidateResult.class);
        assertThat(((EvaluationResult.ValidateResult) result).valid()).isTrue();
    }

    @Test
    void evaluate_returnsInvalid_whenEqCompareDoesNotMatch() {
        String json = """
                {"compare":{"field":"status","op":"EQ","value":"active"}}""";
        RuleNode rule = parser.parse(json);
        EvaluationInput input = EvaluationInput.of(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Map.of("status", "closed"), Map.of());

        EvaluationResult result = evaluator.evaluate(rule, input);

        assertThat(result).isInstanceOf(EvaluationResult.ValidateResult.class);
        assertThat(((EvaluationResult.ValidateResult) result).valid()).isFalse();
    }

    @Test
    void evaluate_returnsValid_whenAndConditionAllMatch() {
        String json = """
                {"and":[
                  {"compare":{"field":"status","op":"EQ","value":"active"}},
                  {"compare":{"field":"riskLevel","op":"GT","value":0}}
                ]}""";
        RuleNode rule = parser.parse(json);
        EvaluationInput input = EvaluationInput.of(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Map.of("status", "active", "riskLevel", 3), Map.of());

        EvaluationResult result = evaluator.evaluate(rule, input);

        assertThat(((EvaluationResult.ValidateResult) result).valid()).isTrue();
    }

    @Test
    void evaluate_returnsInvalid_whenAndConditionPartiallyFails() {
        String json = """
                {"and":[
                  {"compare":{"field":"status","op":"EQ","value":"active"}},
                  {"compare":{"field":"riskLevel","op":"GT","value":5}}
                ]}""";
        RuleNode rule = parser.parse(json);
        EvaluationInput input = EvaluationInput.of(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Map.of("status", "active", "riskLevel", 3), Map.of());

        EvaluationResult result = evaluator.evaluate(rule, input);

        assertThat(((EvaluationResult.ValidateResult) result).valid()).isFalse();
    }

    @Test
    void evaluate_returnsValid_whenOrConditionAnyMatch() {
        String json = """
                {"or":[
                  {"compare":{"field":"severity","op":"EQ","value":"LOW"}},
                  {"compare":{"field":"severity","op":"EQ","value":"MEDIUM"}}
                ]}""";
        RuleNode rule = parser.parse(json);
        EvaluationInput input = EvaluationInput.of(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Map.of("severity", "MEDIUM"), Map.of());

        EvaluationResult result = evaluator.evaluate(rule, input);

        assertThat(((EvaluationResult.ValidateResult) result).valid()).isTrue();
    }

    @Test
    void evaluate_returnsInvalid_whenNotConditionIsTrue() {
        String json = """
                {"not":{"compare":{"field":"deleted","op":"EQ","value":false}}}""";
        RuleNode rule = parser.parse(json);
        EvaluationInput input = EvaluationInput.of(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Map.of("deleted", false), Map.of());

        EvaluationResult result = evaluator.evaluate(rule, input);

        // not(false == false) = not(true) = false — invalid
        assertThat(((EvaluationResult.ValidateResult) result).valid()).isFalse();
    }

    @Test
    void evaluate_handlesGtCompare() {
        String json = """
                {"compare":{"field":"riskScore","op":"GT","value":5}}""";
        RuleNode rule = parser.parse(json);
        EvaluationInput input = EvaluationInput.of(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Map.of("riskScore", 7), Map.of());

        EvaluationResult result = evaluator.evaluate(rule, input);

        assertThat(((EvaluationResult.ValidateResult) result).valid()).isTrue();
    }
}
