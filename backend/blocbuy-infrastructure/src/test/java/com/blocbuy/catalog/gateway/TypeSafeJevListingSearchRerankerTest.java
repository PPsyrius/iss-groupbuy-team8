package com.blocbuy.catalog.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.catalog.gateway.ListingSearchReranker.Candidate;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

class TypeSafeJevListingSearchRerankerTest {

    private static final UUID FIRST = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID SECOND = UUID.fromString("a0000000-0000-7000-8000-000000000002");

    /** Verifies the typed Jev request and descending probability order. */
    @Test
    void sendsTypedChoiceAndOrdersByProbability() {
        AtomicReference<MockClientHttpRequest> captured = new AtomicReference<>();
        RestClient client =
                RestClient.builder()
                        .requestFactory(
                                (uri, method) -> {
                                    MockClientHttpRequest request =
                                            new MockClientHttpRequest(method, uri);
                                    captured.set(request);
                                    request.setResponse(
                                            response(
                                                    """
                                                    {"answers":{"best_match":{"type":"choice",
                                                    "choice":"%s","probabilities":{"%s":0.2,
                                                    "%s":0.8},"confidence":0.7}}}
                                                    """
                                                            .formatted(SECOND, FIRST, SECOND),
                                                    HttpStatus.OK));
                                    return request;
                                })
                        .build();
        TypeSafeJevListingSearchReranker reranker = reranker(true, client);

        List<UUID> result = reranker.rerank("quiet keyboard", candidates());

        assertThat(result).containsExactly(SECOND, FIRST);
        assertThat(captured.get().getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(captured.get().getURI()).hasToString("https://api.typesafe.ai/v1/systemone");
        assertThat(captured.get().getHeaders().getFirst("Authorization"))
                .isEqualTo("Bearer test-key");
        assertThat(captured.get().getBodyAsString())
                .contains("jev-test", "quiet keyboard", "best_match", FIRST.toString());
    }

    /** Verifies that disabling the integration avoids HTTP and preserves order. */
    @Test
    void disabledIntegrationReturnsOriginalOrderWithoutHttp() {
        TypeSafeJevListingSearchReranker reranker = reranker(false, RestClient.builder().build());

        assertThat(reranker.rerank("keyboard", candidates())).containsExactly(FIRST, SECOND);
    }

    /** Verifies that one candidate does not require a provider call. */
    @Test
    void oneCandidateReturnsWithoutHttp() {
        TypeSafeJevListingSearchReranker reranker = reranker(true, RestClient.builder().build());

        assertThat(reranker.rerank("keyboard", List.of(candidates().get(0))))
                .containsExactly(FIRST);
    }

    /** Verifies fail-open ordering when the provider request fails. */
    @Test
    void providerFailureReturnsOriginalOrder() {
        RestClient client =
                RestClient.builder()
                        .requestFactory(
                                (uri, method) -> {
                                    throw new IOException("offline");
                                })
                        .build();

        assertThat(reranker(true, client).rerank("keyboard", candidates()))
                .containsExactly(FIRST, SECOND);
    }

    /** Verifies rejection of malformed, partial, and inconsistent provider answers. */
    @Test
    void malformedOrIncompleteAnswersReturnOriginalOrder() {
        RestClient nullAnswers = stub("{}", HttpStatus.OK);
        RestClient missing = stub("{\"answers\":{}}", HttpStatus.OK);
        RestClient wrongType =
                stub(
                        """
                        {"answers":{"best_match":{"type":"noul",
                        "probabilities":{"%s":0.8,"%s":0.2}}}}
                        """
                                .formatted(FIRST, SECOND),
                        HttpStatus.OK);
        RestClient missingScore =
                stub(
                        """
                        {"answers":{"best_match":{"type":"choice",
                        "probabilities":{"%s":1.0}}}}
                        """
                                .formatted(FIRST),
                        HttpStatus.OK);
        RestClient invalidScore =
                stub(
                        """
                        {"answers":{"best_match":{"type":"choice","choice":"%s",
                        "probabilities":{"%s":1.1,"%s":-0.1},"confidence":0.5}}}
                        """
                                .formatted(FIRST, FIRST, SECOND),
                        HttpStatus.OK);
        RestClient invalidTotal =
                stub(
                        """
                        {"answers":{"best_match":{"type":"choice","choice":"%s",
                        "probabilities":{"%s":0.8,"%s":0.7},"confidence":0.5}}}
                        """
                                .formatted(FIRST, FIRST, SECOND),
                        HttpStatus.OK);
        UUID unexpected = UUID.fromString("a0000000-0000-7000-8000-000000000099");
        RestClient unexpectedKey =
                stub(
                        """
                        {"answers":{"best_match":{"type":"choice","choice":"%s",
                        "probabilities":{"%s":0.2,"%s":0.8,"%s":0.0},
                        "confidence":0.5}}}
                        """
                                .formatted(SECOND, FIRST, SECOND, unexpected),
                        HttpStatus.OK);

        assertThat(reranker(true, nullAnswers).rerank("keyboard", candidates()))
                .containsExactly(FIRST, SECOND);
        assertThat(reranker(true, missing).rerank("keyboard", candidates()))
                .containsExactly(FIRST, SECOND);
        assertThat(reranker(true, wrongType).rerank("keyboard", candidates()))
                .containsExactly(FIRST, SECOND);
        assertThat(reranker(true, missingScore).rerank("keyboard", candidates()))
                .containsExactly(FIRST, SECOND);
        assertThat(reranker(true, invalidScore).rerank("keyboard", candidates()))
                .containsExactly(FIRST, SECOND);
        assertThat(reranker(true, invalidTotal).rerank("keyboard", candidates()))
                .containsExactly(FIRST, SECOND);
        assertThat(reranker(true, unexpectedKey).rerank("keyboard", candidates()))
                .containsExactly(FIRST, SECOND);
    }

    /** Verifies the provider shortlist cap and stable append of remaining candidates. */
    @Test
    void boundsProviderShortlistAndAppendsRemainingCandidates() {
        java.util.Map<String, Double> probabilities = new java.util.LinkedHashMap<>();
        java.util.List<Candidate> many =
                java.util.stream.IntStream.rangeClosed(1, 21)
                        .mapToObj(
                                index ->
                                        new Candidate(
                                                new UUID(0, index),
                                                "Product " + index,
                                                null,
                                                "short description",
                                                "test"))
                        .toList();

        // A valid distribution whose scores increase with the candidate index.
        // 1 + ... + 20 = 210, so these probabilities sum exactly to 1 mathematically.
        for (int index = 1; index <= 20; index++) {
            probabilities.put(new UUID(0, index).toString(), index / 210.0);
        }

        StringBuilder scores = new StringBuilder();
        probabilities.forEach(
                (id, score) -> {
                    if (!scores.isEmpty()) {
                        scores.append(',');
                    }
                    scores.append('"').append(id).append("\":").append(score);
                });
        RestClient client =
                stub(
                        "{\"answers\":{\"best_match\":{\"type\":\"choice\","
                                + "\"probabilities\":{"
                                + scores
                                + "}}}}",
                        HttpStatus.OK);

        java.util.List<UUID> result = reranker(true, client).rerank("product", many);

        assertThat(result.get(0)).isEqualTo(new UUID(0, 20));
        assertThat(result.get(20)).isEqualTo(new UUID(0, 21));
    }

    /** Verifies original ordering for missing responses and missing probabilities. */
    @Test
    void nullResponseOrProbabilitiesPreserveOriginalOrder() {
        java.util.List<UUID> original = java.util.List.of(FIRST, SECOND);
        java.util.List<Candidate> shortlist = candidates();

        assertThat(TypeSafeJevListingSearchReranker.orderedIds(null, shortlist, original))
                .isEqualTo(original);
        assertThat(
                        TypeSafeJevListingSearchReranker.orderedIds(
                                new TypeSafeJevListingSearchReranker.SystemOneResponse(null),
                                shortlist,
                                original))
                .isEqualTo(original);
        assertThat(
                        TypeSafeJevListingSearchReranker.orderedIds(
                                new TypeSafeJevListingSearchReranker.SystemOneResponse(
                                        java.util.Map.of(
                                                "best_match",
                                                new TypeSafeJevListingSearchReranker.ChoiceAnswer(
                                                        "choice", null, null, null))),
                                shortlist,
                                original))
                .isEqualTo(original);
    }

    /** Verifies score validation and provider-facing text bounds. */
    @Test
    void validatesScoresAndBoundsProviderText() {
        assertThat(TypeSafeJevListingSearchReranker.isValidScore(null)).isFalse();
        assertThat(TypeSafeJevListingSearchReranker.isValidScore(Double.NaN)).isFalse();
        assertThat(TypeSafeJevListingSearchReranker.isValidScore(-0.1)).isFalse();
        assertThat(TypeSafeJevListingSearchReranker.isValidScore(1.1)).isFalse();
        assertThat(TypeSafeJevListingSearchReranker.isValidScore(0.5)).isTrue();
        assertThat(TypeSafeJevListingSearchReranker.truncate(null)).isNull();
        assertThat(TypeSafeJevListingSearchReranker.truncate("short")).isEqualTo("short");
        assertThat(TypeSafeJevListingSearchReranker.truncate("x".repeat(501))).hasSize(500);
    }

    /** Builds the adapter under test with explicit enablement and an injected client. */
    private static TypeSafeJevListingSearchReranker reranker(boolean enabled, RestClient client) {
        return new TypeSafeJevListingSearchReranker(
                new TypeSafeJevProperties(
                        enabled, enabled ? "test-key" : null, null, "jev-test", 100, 100),
                client);
    }

    /** Returns the stable two-product fixture shared by ranking scenarios. */
    private static List<Candidate> candidates() {
        return List.of(
                new Candidate(FIRST, "Office keyboard", null, "A".repeat(600), "electronics"),
                new Candidate(SECOND, "Quiet keyboard", "Silent", null, "electronics"));
    }

    /** Builds a RestClient backed by a deterministic in-memory response. */
    private static RestClient stub(String json, HttpStatus status) {
        return RestClient.builder()
                .requestFactory(
                        (uri, method) -> {
                            MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
                            request.setResponse(response(json, status));
                            return request;
                        })
                .build();
    }

    /** Encodes a synthetic provider payload as a client response. */
    private static MockClientHttpResponse response(String json, HttpStatus status) {
        MockClientHttpResponse response =
                new MockClientHttpResponse(json.getBytes(StandardCharsets.UTF_8), status);
        response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return response;
    }
}
