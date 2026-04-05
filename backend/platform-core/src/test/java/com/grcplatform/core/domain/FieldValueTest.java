package com.grcplatform.core.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import main.java.com.grcplatform.core.domain.FieldValue;

class FieldValueTest {

    @Test
    void textValue_storesText() {
        FieldValue value = new FieldValue.TextValue("hello");
        assertThat(value).isInstanceOf(FieldValue.TextValue.class);
        assertThat(((FieldValue.TextValue) value).text()).isEqualTo("hello");
    }

    @Test
    void numericValue_storesBigDecimal() {
        FieldValue value = new FieldValue.NumericValue(BigDecimal.valueOf(3.14));
        assertThat(((FieldValue.NumericValue) value).value()).isEqualByComparingTo("3.14");
    }

    @Test
    void multiSelectValue_isImmutable() {
        FieldValue value = new FieldValue.MultiSelectValue(List.of("A", "B"));
        assertThatThrownBy(() -> ((FieldValue.MultiSelectValue) value).values().add("C"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void switchDispatch_handlesAllVariants() {
        List<FieldValue> values = List.of(
                new FieldValue.TextValue("text"),
                new FieldValue.NumericValue(BigDecimal.ONE),
                new FieldValue.DateValue(LocalDate.of(2026, 1, 1)),
                new FieldValue.BooleanValue(true),
                new FieldValue.ReferenceValue(UUID.randomUUID(), "Label"),
                new FieldValue.MultiSelectValue(List.of("X"))
        );

        for (FieldValue value : values) {
            String result = switch (value) {
                case FieldValue.TextValue tv       -> "text:" + tv.text();
                case FieldValue.NumericValue nv    -> "num:" + nv.value();
                case FieldValue.DateValue dv       -> "date:" + dv.date();
                case FieldValue.BooleanValue bv    -> "bool:" + bv.flag();
                case FieldValue.ReferenceValue rv  -> "ref:" + rv.displayLabel();
                case FieldValue.MultiSelectValue mv -> "multi:" + mv.values();
            };
            assertThat(result).isNotBlank();
        }
    }
}
