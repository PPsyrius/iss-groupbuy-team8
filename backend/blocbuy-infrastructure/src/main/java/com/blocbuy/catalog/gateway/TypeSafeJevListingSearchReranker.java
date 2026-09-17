package com.blocbuy.catalog.gateway;

import com.blocbuy.catalog.gateway.ListingSearchReranker.Candidate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** TypeSafe Jev adapter that semantically re-ranks at most one ordinary result page. */
@Slf4j
@Component
@EnableConfigurationProperties(TypeSafeJevProperties.class)
public class TypeSafeJevListingSearchReranker implements ListingSearchReranker {

    private static final int MAX_CANDIDATES = 20;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private final TypeSafeJevProperties properties;
    private final RestClient restClient;

    @Autowired
    public TypeSafeJevListingSearchReranker(TypeSafeJevProperties properties) {
        this(properties, defaultRestClient(properties));
    }

    TypeSafeJevListingSearchReranker(TypeSafeJevProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public List<UUID> rerank(String keyword, List<Candidate> candidates) {
        List<UUID> original = candidates.stream().map(Candidate::id).toList();
        if (!properties.enabled() || candidates.size() < 2) {
            return original;
        }

        List<Candidate> shortlist = candidates.stream().limit(MAX_CANDIDATES).toList();
        Map<String, Object> criteria = new LinkedHashMap<>();
        shortlist.forEach(
                candidate ->
                        criteria.put(
                                candidate.id().toString(),
                                new CandidateState(
                                        candidate.title(),
                                        candidate.subtitle(),
                                        truncate(candidate.description()),
                                        candidate.category())));
        SystemOneRequest request =
                new SystemOneRequest(
                        Map.of("shopper_query", keyword),
                        properties.model(),
                        Map.of(
                                "best_match",
                                new ChoiceQuestion(
                                        "Rank the listed products by how well each one satisfies "
                                                + "the shopper intent in `shopper_query`.",
                                        criteria)));

        try {
            SystemOneResponse response =
                    restClient
                            .post()
                            .uri(properties.endpoint())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(SystemOneResponse.class);
            return orderedIds(response, shortlist, original);
        } catch (RestClientException ex) {
            log.debug("TypeSafe Jev re-ranking unavailable: {}", ex.getClass().getSimpleName());
            return original;
        }
    }

    static List<UUID> orderedIds(
            SystemOneResponse response, List<Candidate> shortlist, List<UUID> original) {
        if (response == null || response.answers() == null) {
            return original;
        }
        ChoiceAnswer answer = response.answers().get("best_match");
        if (answer == null || !"choice".equals(answer.type()) || answer.probabilities() == null) {
            return original;
        }

        Map<UUID, Double> scores = new LinkedHashMap<>();
        for (Candidate candidate : shortlist) {
            Double score = answer.probabilities().get(candidate.id().toString());
            if (!isValidScore(score)) {
                return original;
            }
            scores.put(candidate.id(), score);
        }
        List<UUID> ranked = new ArrayList<>(scores.keySet());
        ranked.sort(Comparator.comparingDouble(scores::get).reversed());
        original.stream().filter(id -> !scores.containsKey(id)).forEach(ranked::add);
        return List.copyOf(ranked);
    }

    static boolean isValidScore(Double score) {
        return score != null && Double.isFinite(score) && score >= 0 && score <= 1;
    }

    static String truncate(String value) {
        if (value == null || value.length() <= MAX_DESCRIPTION_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_DESCRIPTION_LENGTH);
    }

    private static RestClient defaultRestClient(TypeSafeJevProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()));
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis()));
        return RestClient.builder().requestFactory(factory).build();
    }

    private record SystemOneRequest(
            Object state, String model, Map<String, ChoiceQuestion> questions) {}

    private record ChoiceQuestion(String type, Object instructions, Map<String, Object> criteria) {
        private ChoiceQuestion(Object instructions, Map<String, Object> criteria) {
            this("choice", instructions, criteria);
        }
    }

    private record CandidateState(
            String title, String subtitle, String description, String category) {}

    record SystemOneResponse(Map<String, ChoiceAnswer> answers) {}

    record ChoiceAnswer(
            String type, String choice, Map<String, Double> probabilities, Double confidence) {}
}
