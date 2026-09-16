package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import com.blocbuy.common.web.GlobalExceptionHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Handler unit contract: business details ride the envelope with the enum's status and code. Direct
 * method calls cover mappings that MockMvc cannot reach in isolation — unknown paths are rejected
 * by the auth interceptor before resource resolution.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessDetailsRideTheEnvelope() {
        var response =
                handler.handleBiz(
                        new BizException(
                                CommonErrorCode.VALIDATION_FAILED, Map.of("field", "reason")));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("COMMON_400_002");
        assertThat(response.getBody().errors()).isEqualTo(Map.of("field", "reason"));
    }

    @Test
    void unknownResourceMapsToNotFound() {
        var response = handler.handleNoResource(mock(NoResourceFoundException.class));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("COMMON_404_001");
    }
}
