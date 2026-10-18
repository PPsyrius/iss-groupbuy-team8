package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import com.blocbuy.common.web.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
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

    @Test
    void methodNotAllowedMapsTo405WithAllowHeader() {
        var response =
                handler.handleMethodNotAllowed(
                        new HttpRequestMethodNotSupportedException("DELETE", List.of("GET")));

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody().code()).isEqualTo("COMMON_405_001");
        assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET);
    }

    @Test
    void unsupportedMediaTypeMapsTo415WithAcceptHeader() {
        var response =
                handler.handleUnsupportedMediaType(
                        new HttpMediaTypeNotSupportedException(
                                MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(response.getBody().code()).isEqualTo("COMMON_415_001");
        assertThat(response.getHeaders().getAccept()).containsExactly(MediaType.APPLICATION_JSON);
    }

    @Test
    void notAcceptableMapsTo406WithJsonContentType() {
        var response =
                handler.handleNotAcceptable(
                        new HttpMediaTypeNotAcceptableException(
                                List.of(MediaType.APPLICATION_JSON)));

        assertThat(response.getStatusCode().value()).isEqualTo(406);
        assertThat(response.getBody().code()).isEqualTo("COMMON_406_001");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }
}
