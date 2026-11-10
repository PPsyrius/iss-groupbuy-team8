package com.blocbuy.groupbuy.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.groupbuy.query.OpenDealOffer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Batch protocol and failure policy, with the real versioned JSON codec. */
class RedisOpenDealOfferCacheTest {
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID MISSING = UUID.randomUUID();
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private final ObjectMapper json = new ObjectMapper();
    private final RedisOpenDealOfferCache cache =
            new RedisOpenDealOfferCache(
                    redis,
                    new MockEnvironment().withProperty("spring.profiles.active", "test"),
                    json);

    @BeforeEach
    void operations() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void oneMgetDistinguishesPositiveNegativeAndMissingEntries() {
        OpenDealOffer offer = offer(PRODUCT, Instant.now().plusSeconds(3600), 1);
        when(values.multiGet(List.of(key(PRODUCT), key(OTHER), key(MISSING))))
                .thenReturn(
                        Arrays.asList(
                                json.writeValueAsString(
                                        new RedisOpenDealOfferCache.Entry(1, offer)),
                                json.writeValueAsString(new RedisOpenDealOfferCache.Entry(1, null)),
                                null));
        var found = cache.findByProductIds(List.of(PRODUCT, OTHER, MISSING, PRODUCT));
        assertThat(found.available()).isTrue();
        assertThat(found.hits())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(PRODUCT, Optional.of(offer), OTHER, Optional.empty()));
        verify(values).multiGet(List.of(key(PRODUCT), key(OTHER), key(MISSING)));
    }

    @ParameterizedTest
    @MethodSource("unusableValues")
    void corruptUnsupportedForeignExpiredAndFullOffersAreMisses(String value) {
        when(values.multiGet(List.of(key(PRODUCT)))).thenReturn(List.of(value));
        var found = cache.findByProductIds(List.of(PRODUCT));
        assertThat(found.available()).isTrue();
        assertThat(found.hits()).isEmpty();
    }

    static Stream<String> unusableValues() {
        ObjectMapper json = new ObjectMapper();
        return Stream.of(
                "not-json",
                "null",
                "{\"version\":2}",
                json.writeValueAsString(
                        new RedisOpenDealOfferCache.Entry(
                                1, offer(OTHER, Instant.now().plusSeconds(3600), 1))),
                json.writeValueAsString(
                        new RedisOpenDealOfferCache.Entry(
                                1, offer(PRODUCT, Instant.now().minusSeconds(1), 1))),
                json.writeValueAsString(
                        new RedisOpenDealOfferCache.Entry(
                                1, offer(PRODUCT, Instant.now().plusSeconds(3600), 5))),
                "{\"version\":1,\"offer\":{\"dealId\":null}}");
    }

    @Test
    void emptyInputsDoNoRedisWork() {
        assertThat(cache.findByProductIds(List.of()).hits()).isEmpty();
        cache.putAll(Map.of());
        cache.evict(List.of());
        verifyNoInteractions(redis);
    }

    @Test
    void aMissingBatchResponseDoesNotTriggerAnotherStoreWait() {
        when(values.multiGet(List.of(key(PRODUCT)))).thenReturn(null);
        var found = cache.findByProductIds(List.of(PRODUCT));
        assertThat(found.available()).isFalse();
        assertThat(found.hits()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void readWriteAndEvictionFailuresAreBestEffort() {
        when(values.multiGet(any()))
                .thenThrow(new RedisConnectionFailureException("private detail"));
        var found = cache.findByProductIds(List.of(PRODUCT));
        assertThat(found.available()).isFalse();
        assertThat(found.hits()).isEmpty();
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("private detail"));
        assertThatCode(() ->
                        cache.putAll(
                                Map.of(
                                        PRODUCT,
                                        Optional.of(
                                                offer(PRODUCT, Instant.now().plusSeconds(3600), 1)),
                                        OTHER,
                                        Optional.empty())))
                .doesNotThrowAnyException();
        doThrow(new RedisConnectionFailureException("private detail"))
                .when(redis)
                .delete(anyList());
        assertThatCode(() -> cache.evict(List.of(PRODUCT))).doesNotThrowAnyException();
    }

    @Test
    void evictionsUseOneDeduplicatedDelete() {
        cache.evict(List.of(PRODUCT, OTHER, PRODUCT));
        verify(redis).delete(List.of(key(PRODUCT), key(OTHER)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void onePopulationBatchPairsEachValueWithItsOwnTtl() {
        OpenDealOffer offer = offer(PRODUCT, Instant.now().plusSeconds(3600), 1);
        cache.putAll(Map.of(PRODUCT, Optional.of(offer), OTHER, Optional.empty()));
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(redis).execute(any(RedisScript.class), keys.capture(), arguments.capture());
        assertThat(keys.getValue()).containsExactlyInAnyOrder(key(PRODUCT), key(OTHER));
        assertThat(arguments.getValue()).hasSize(4);
        for (int i = 0; i < keys.getValue().size(); i++) {
            var entry =
                    json.readValue(
                            (String) arguments.getValue()[2 * i],
                            RedisOpenDealOfferCache.Entry.class);
            long ttl = Long.parseLong((String) arguments.getValue()[2 * i + 1]);
            assertThat(entry.version()).isEqualTo(1);
            if (keys.getValue().get(i).equals(key(PRODUCT))) {
                assertThat(entry.offer()).isEqualTo(offer);
                assertThat(ttl).isBetween(15_000L, 25_000L);
            } else {
                assertThat(entry.offer()).isNull();
                assertThat(ttl).isBetween(5_000L, 10_000L);
            }
        }
    }

    @Test
    void serializationFailureDoesNotBreakTheSourceResult() {
        ObjectMapper broken = mock(ObjectMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(mock(JacksonException.class));
        var unavailableCodec = new RedisOpenDealOfferCache(redis, new MockEnvironment(), broken);
        assertThatCode(() ->
                        unavailableCodec.putAll(
                                Map.of(
                                        PRODUCT,
                                        Optional.of(
                                                offer(
                                                        PRODUCT,
                                                        Instant.now().plusSeconds(3600),
                                                        1)))))
                .doesNotThrowAnyException();
        verifyNoInteractions(redis);
    }

    private static OpenDealOffer offer(UUID product, Instant expiry, int current) {
        return new OpenDealOffer(
                UUID.randomUUID(), product, new BigDecimal("89.00"), "sgd", 5, current, expiry);
    }

    private static String key(UUID product) {
        return "blocbuy:test:groupbuy:best-open-deal:" + product;
    }
}
