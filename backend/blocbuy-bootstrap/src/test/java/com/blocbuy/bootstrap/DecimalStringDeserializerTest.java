package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.web.DecimalStringDeserializer;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Exact decimal-string contract: strings pass through, numbers are rejected, never coerced. */
class DecimalStringDeserializerTest {

    record AmountForm(@JsonDeserialize(using = DecimalStringDeserializer.class) String amount) {}

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void stringAmountPassesThrough() throws Exception {
        assertThat(json.readValue("{\"amount\":\"12.00\"}", AmountForm.class).amount())
                .isEqualTo("12.00");
    }

    @Test
    void numericAmountIsRejected() {
        assertThatThrownBy(() -> json.readValue("{\"amount\":12.00}", AmountForm.class))
                .isInstanceOf(JacksonException.class);
    }
}
