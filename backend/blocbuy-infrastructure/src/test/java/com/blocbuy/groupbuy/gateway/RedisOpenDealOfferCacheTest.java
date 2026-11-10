package com.blocbuy.groupbuy.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.groupbuy.query.OpenDealOffer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void codecWorkDoesNotBlockAnotherStoreCall(boolean decoding) throws Exception {
        int limit = 16;
        var entered = new CountDownLatch(limit);
        var release = new CountDownLatch(1);
        var codecCalls = new AtomicInteger();
        ObjectMapper codec = mock(ObjectMapper.class);
        var protectedCache = new RedisOpenDealOfferCache(redis, new MockEnvironment(), codec);
        String absent = "{\"version\":1,\"offer\":null}";
        if (decoding) {
            when(values.multiGet(anyList())).thenReturn(List.of(absent));
            when(codec.readValue(anyString(), eq(RedisOpenDealOfferCache.Entry.class)))
                    .thenAnswer(
                            invocation -> {
                                awaitCodec(entered, release, codecCalls, limit);
                                return new RedisOpenDealOfferCache.Entry(1, null);
                            });
        } else {
            when(values.multiGet(anyList())).thenReturn(Collections.singletonList(null));
            when(codec.writeValueAsString(any()))
                    .thenAnswer(
                            invocation -> {
                                awaitCodec(entered, release, codecCalls, limit);
                                return absent;
                            });
        }
        try (var workers = Executors.newFixedThreadPool(limit)) {
            var pending = new ArrayList<java.util.concurrent.Future<?>>();
            try {
                for (int i = 0; i < limit; i++) {
                    pending.add(
                            workers.submit(
                                    () -> {
                                        if (decoding) {
                                            protectedCache.findByProductIds(List.of(PRODUCT));
                                        } else {
                                            protectedCache.putAll(
                                                    Map.of(PRODUCT, Optional.empty()));
                                        }
                                    }));
                }
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(protectedCache.findByProductIds(List.of(PRODUCT)).available()).isTrue();
                verify(values, times(decoding ? limit + 1 : 1)).multiGet(anyList());
            } finally {
                release.countDown();
            }
            for (var operation : pending) {
                operation.get(10, TimeUnit.SECONDS);
            }
        }
    }

    private static void awaitCodec(
            CountDownLatch entered, CountDownLatch release, AtomicInteger calls, int limit)
            throws InterruptedException {
        if (calls.incrementAndGet() <= limit) {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void healthyStoreConcurrencyDoesNotManufactureUnavailability() throws Exception {
        int concurrentReads = 17;
        var entered = new CountDownLatch(concurrentReads);
        var release = new CountDownLatch(1);
        when(values.multiGet(anyList()))
                .thenAnswer(
                        invocation -> {
                            entered.countDown();
                            assertThat(release.await(30, TimeUnit.SECONDS)).isTrue();
                            return Collections.singletonList(null);
                        });
        try (var workers = Executors.newFixedThreadPool(concurrentReads)) {
            var pending = new ArrayList<java.util.concurrent.Future<OpenDealOfferCache.Lookup>>();
            try {
                for (int i = 0; i < concurrentReads; i++) {
                    pending.add(workers.submit(() -> cache.findByProductIds(List.of(PRODUCT))));
                }
                assertThat(entered.await(5, TimeUnit.SECONDS))
                        .as(
                                "Healthy store calls must not be rejected by a fixed display-slot count")
                        .isTrue();
            } finally {
                release.countDown();
            }
            for (var operation : pending) {
                assertThat(operation.get(10, TimeUnit.SECONDS).available()).isTrue();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, Long.MAX_VALUE - 500_000_000L})
    @SuppressWarnings("unchecked")
    void cooldownSkipsStoreWorkUntilTheExactMonotonicBoundary(long start) {
        var now = new AtomicLong(start);
        var guarded = timedCache(now);
        when(values.multiGet(anyList()))
                .thenThrow(new RedisConnectionFailureException("private detail"))
                .thenReturn(Collections.singletonList(null));
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
        now.addAndGet(TimeUnit.SECONDS.toNanos(1) - 1);
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
        guarded.putAll(Map.of(PRODUCT, Optional.empty()));
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
        guarded.evict(List.of(PRODUCT));
        verify(redis).delete(List.of(key(PRODUCT)));
        now.incrementAndGet();
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isTrue();
        guarded.putAll(Map.of(PRODUCT, Optional.empty()));
        verify(values, times(2)).multiGet(anyList());
        verify(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void missingBatchResponseBacksOffAndRecovers() {
        var now = new AtomicLong();
        var guarded = timedCache(now);
        when(values.multiGet(anyList()))
                .thenReturn(null)
                .thenReturn(Collections.singletonList(null));
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
        now.addAndGet(TimeUnit.SECONDS.toNanos(1));
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isTrue();
        verify(values, times(2)).multiGet(anyList());
    }

    @Test
    void failedReadProbeRestartsCooldown() {
        var now = new AtomicLong();
        var guarded = timedCache(now);
        when(values.multiGet(anyList()))
                .thenThrow(new RedisConnectionFailureException("private detail"))
                .thenThrow(new RedisConnectionFailureException("private detail"))
                .thenReturn(Collections.singletonList(null));
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
        now.addAndGet(TimeUnit.SECONDS.toNanos(1));
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
        now.addAndGet(TimeUnit.SECONDS.toNanos(1));
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isTrue();
        verify(values, times(3)).multiGet(anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void populationFailureAlsoBacksOffReadsAndAllowsAWriteProbe() {
        var now = new AtomicLong();
        var guarded = timedCache(now);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("private detail"))
                .thenThrow(new RedisConnectionFailureException("private detail"))
                .thenReturn(1L);
        when(values.multiGet(anyList())).thenReturn(Collections.singletonList(null));
        guarded.putAll(Map.of(PRODUCT, Optional.empty()));
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
        now.addAndGet(TimeUnit.SECONDS.toNanos(1));
        guarded.putAll(Map.of(PRODUCT, Optional.empty()));
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
        verifyNoInteractions(values);
        now.addAndGet(TimeUnit.SECONDS.toNanos(1));
        guarded.putAll(Map.of(PRODUCT, Optional.empty()));
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isTrue();
        verify(redis, times(3)).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void onlyOneRecoveryProbeIsSharedByReadsAndPopulation(boolean population) throws Exception {
        var now = new AtomicLong();
        var guarded = timedCache(now);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var reads = new AtomicInteger();
        when(values.multiGet(anyList()))
                .thenAnswer(
                        invocation -> {
                            if (reads.incrementAndGet() == 1) {
                                throw new RedisConnectionFailureException("private detail");
                            }
                            if (!population) {
                                entered.countDown();
                                assertThat(release.await(30, TimeUnit.SECONDS)).isTrue();
                            }
                            return Collections.singletonList(null);
                        });
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenAnswer(
                        invocation -> {
                            entered.countDown();
                            assertThat(release.await(30, TimeUnit.SECONDS)).isTrue();
                            return 1L;
                        });
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
        now.addAndGet(TimeUnit.SECONDS.toNanos(1));
        try (var worker = Executors.newSingleThreadExecutor()) {
            var probe =
                    worker.submit(
                            () -> {
                                if (population) {
                                    guarded.putAll(Map.of(PRODUCT, Optional.empty()));
                                    return null;
                                }
                                return guarded.findByProductIds(List.of(PRODUCT));
                            });
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
                guarded.putAll(Map.of(PRODUCT, Optional.empty()));
            } finally {
                release.countDown();
            }
            if (population) {
                probe.get(10, TimeUnit.SECONDS);
            } else {
                assertThat(probe.get(10, TimeUnit.SECONDS).available()).isTrue();
            }
        }
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isTrue();
        verify(values, times(population ? 2 : 3)).multiGet(anyList());
        verify(redis, times(population ? 1 : 0))
                .execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void serializationFailureDoesNotConsumeARecoveryProbe() {
        var now = new AtomicLong();
        var broken = mock(ObjectMapper.class);
        var guarded = new RedisOpenDealOfferCache(redis, new MockEnvironment(), broken, now::get);
        when(broken.writeValueAsString(any())).thenThrow(mock(JacksonException.class));
        when(values.multiGet(anyList()))
                .thenThrow(new RedisConnectionFailureException("private detail"))
                .thenReturn(Collections.singletonList(null));
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
        now.addAndGet(TimeUnit.SECONDS.toNanos(1));
        guarded.putAll(Map.of(PRODUCT, Optional.empty()));
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isTrue();
        verify(values, times(2)).multiGet(anyList());
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void unexpectedStoreExceptionCannotLeaveARecoveryProbeOccupied(boolean population) {
        var now = new AtomicLong();
        var guarded = timedCache(now);
        when(values.multiGet(anyList()))
                .thenThrow(new RedisConnectionFailureException("private detail"))
                .thenReturn(Collections.singletonList(null));
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
        now.addAndGet(TimeUnit.SECONDS.toNanos(1));
        if (population) {
            when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenThrow(new IllegalStateException("private detail"))
                    .thenReturn(1L);
            assertThatThrownBy(() -> guarded.putAll(Map.of(PRODUCT, Optional.empty())))
                    .isInstanceOf(IllegalStateException.class);
            guarded.putAll(Map.of(PRODUCT, Optional.empty()));
        } else {
            when(values.multiGet(anyList()))
                    .thenThrow(new IllegalStateException("private detail"))
                    .thenReturn(Collections.singletonList(null));
            assertThatThrownBy(() -> guarded.findByProductIds(List.of(PRODUCT)))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void staleRecoveryAdmissionCannotReplaceANewerFailure() {
        var now = new AtomicLong();
        var interleaveFailure = new AtomicBoolean();
        var reference = new AtomicReference<RedisOpenDealOfferCache>();
        var guarded =
                new RedisOpenDealOfferCache(
                        redis,
                        new MockEnvironment(),
                        json,
                        () -> {
                            if (interleaveFailure.compareAndSet(true, false)) {
                                reference.get().putAll(Map.of(PRODUCT, Optional.empty()));
                            }
                            return now.get();
                        });
        reference.set(guarded);
        when(values.multiGet(anyList()))
                .thenThrow(new RedisConnectionFailureException("private detail"));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("private detail"));
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
        now.addAndGet(TimeUnit.SECONDS.toNanos(1));
        interleaveFailure.set(true);
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
        assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
        verify(values).multiGet(anyList());
        verify(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void olderFailureCannotReplaceANewerCooldownOrProbe(boolean newerProbe) throws Exception {
        long start = Long.MAX_VALUE - 500_000_000L;
        var now = new AtomicLong(start);
        var oldSampled = new CountDownLatch(1);
        var publishOld = new CountDownLatch(1);
        var probeEntered = new CountDownLatch(1);
        var finishProbe = new CountDownLatch(1);
        var firstSample = new AtomicBoolean(true);
        var guarded =
                new RedisOpenDealOfferCache(
                        redis,
                        new MockEnvironment(),
                        json,
                        () -> {
                            long sampled = now.get();
                            if (firstSample.compareAndSet(true, false)) {
                                oldSampled.countDown();
                                try {
                                    assertThat(publishOld.await(30, TimeUnit.SECONDS)).isTrue();
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                    throw new AssertionError(interrupted);
                                }
                            }
                            return sampled;
                        });
        var reads = new AtomicInteger();
        when(values.multiGet(anyList()))
                .thenAnswer(
                        invocation -> {
                            int call = reads.incrementAndGet();
                            if (call <= 2) {
                                throw new RedisConnectionFailureException("private detail");
                            }
                            if (newerProbe && call == 3) {
                                probeEntered.countDown();
                                assertThat(finishProbe.await(30, TimeUnit.SECONDS)).isTrue();
                            }
                            return Collections.singletonList(null);
                        });
        try (var workers = Executors.newFixedThreadPool(2)) {
            try {
                var old = workers.submit(() -> guarded.findByProductIds(List.of(PRODUCT)));
                assertThat(oldSampled.await(10, TimeUnit.SECONDS)).isTrue();
                now.addAndGet(500_000_000L);
                assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
                now.addAndGet(500_000_000L);
                if (newerProbe) {
                    now.addAndGet(500_000_000L);
                }
                var probe =
                        workers.submit(
                                () ->
                                        newerProbe
                                                ? guarded.findByProductIds(List.of(PRODUCT))
                                                : null);
                if (newerProbe) {
                    assertThat(probeEntered.await(10, TimeUnit.SECONDS)).isTrue();
                }
                publishOld.countDown();
                assertThat(old.get(10, TimeUnit.SECONDS).available()).isFalse();
                assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
                guarded.putAll(Map.of(PRODUCT, Optional.empty()));
                verify(values, times(newerProbe ? 3 : 2)).multiGet(anyList());
                verify(redis, never())
                        .execute(any(RedisScript.class), anyList(), any(Object[].class));
                if (newerProbe) {
                    finishProbe.countDown();
                    assertThat(probe.get(10, TimeUnit.SECONDS).available()).isTrue();
                } else {
                    now.addAndGet(500_000_000L);
                }
                assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isTrue();
            } finally {
                publishOld.countDown();
                finishProbe.countDown();
            }
        }
    }

    @Test
    void olderProbeSuccessCannotEraseALaterFailure() throws Exception {
        var now = new AtomicLong();
        var guarded = timedCache(now);
        var oldEntered = new CountDownLatch(1);
        var failOld = new CountDownLatch(1);
        var probeEntered = new CountDownLatch(1);
        var finishProbe = new CountDownLatch(1);
        var reads = new AtomicInteger();
        when(values.multiGet(anyList()))
                .thenAnswer(
                        invocation -> {
                            int call = reads.incrementAndGet();
                            if (call == 1) {
                                oldEntered.countDown();
                                assertThat(failOld.await(30, TimeUnit.SECONDS)).isTrue();
                                throw new RedisConnectionFailureException("private detail");
                            }
                            if (call == 2) {
                                throw new RedisConnectionFailureException("private detail");
                            }
                            probeEntered.countDown();
                            assertThat(finishProbe.await(30, TimeUnit.SECONDS)).isTrue();
                            return Collections.singletonList(null);
                        });
        try (var workers = Executors.newFixedThreadPool(2)) {
            try {
                var old = workers.submit(() -> guarded.findByProductIds(List.of(PRODUCT)));
                assertThat(oldEntered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
                now.addAndGet(TimeUnit.SECONDS.toNanos(1));
                var probe = workers.submit(() -> guarded.findByProductIds(List.of(PRODUCT)));
                assertThat(probeEntered.await(10, TimeUnit.SECONDS)).isTrue();
                failOld.countDown();
                assertThat(old.get(10, TimeUnit.SECONDS).available()).isFalse();
                finishProbe.countDown();
                assertThat(probe.get(10, TimeUnit.SECONDS).available()).isTrue();
                assertThat(guarded.findByProductIds(List.of(PRODUCT)).available()).isFalse();
            } finally {
                failOld.countDown();
                finishProbe.countDown();
            }
        }
        verify(values, times(3)).multiGet(anyList());
    }

    private RedisOpenDealOfferCache timedCache(AtomicLong now) {
        return new RedisOpenDealOfferCache(
                redis,
                new MockEnvironment().withProperty("spring.profiles.active", "test"),
                json,
                now::get);
    }

    private static OpenDealOffer offer(UUID product, Instant expiry, int current) {
        return new OpenDealOffer(
                UUID.randomUUID(), product, new BigDecimal("89.00"), "sgd", 5, current, expiry);
    }

    private static String key(UUID product) {
        return "blocbuy:test:groupbuy:best-open-deal:" + product;
    }
}
