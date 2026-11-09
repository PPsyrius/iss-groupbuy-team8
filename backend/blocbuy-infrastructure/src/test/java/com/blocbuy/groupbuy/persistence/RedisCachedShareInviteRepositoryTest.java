package com.blocbuy.groupbuy.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.groupbuy.model.ShareInvite;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

/** Cache protocol and fail-open behavior without changing the invite repository port. */
class RedisCachedShareInviteRepositoryTest {
    private static final String REF = "AAAAAAAAAAAAAAAAAAAAAA";
    private static final String KEY = "blocbuy:it:groupbuy:invite-ref:" + REF;
    private static final ShareInvite INVITE =
            new ShareInvite(UUID.randomUUID(), REF, UUID.randomUUID(), UUID.randomUUID());

    private final ShareInviteRepositoryImpl source = mock(ShareInviteRepositoryImpl.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private final ObjectMapper json = new ObjectMapper();
    private final RedisCachedShareInviteRepository cache =
            new RedisCachedShareInviteRepository(
                    source,
                    redis,
                    new MockEnvironment().withProperty("spring.profiles.active", "it"),
                    json);

    @BeforeEach
    void operations() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void aWarmReferenceSkipsPostgresAndEveryWriteExpiresBeforeTheTeamDeadline() {
        Instant expiry = Instant.now().plusSeconds(60);
        when(source.findByRefWithDeadline(REF))
                .thenReturn(
                        Optional.of(
                                new ShareInviteRepositoryImpl.InviteWithDeadline(INVITE, expiry)));
        String payload =
                json.writeValueAsString(
                        new RedisCachedShareInviteRepository.Entry(1, INVITE, expiry));
        when(values.get(KEY)).thenReturn(null, payload);

        assertThat(cache.findByRef(REF)).contains(INVITE);
        assertThat(cache.findByRef(REF)).contains(INVITE);

        verify(source).findByRefWithDeadline(REF);
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(eq(KEY), eq(payload), ttl.capture());
        assertThat(ttl.getValue()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(30));
        assertThat(ttl.getValue()).isLessThan(Duration.between(Instant.now(), expiry));
    }

    @Test
    void aNearDeadlineReferenceHasNoTtlBeyondItsTeamExpiry() {
        Instant expiry = Instant.now().plusSeconds(5);
        when(source.findByRefWithDeadline(REF))
                .thenReturn(
                        Optional.of(
                                new ShareInviteRepositoryImpl.InviteWithDeadline(INVITE, expiry)));
        assertThat(cache.findByRef(REF)).contains(INVITE);
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(eq(KEY), anyString(), ttl.capture());
        assertThat(ttl.getValue()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void anExpiredOrMissingTeamDeadlineKeepsThePostgresResultWithoutCaching() {
        when(source.findByRefWithDeadline(REF))
                .thenReturn(
                        Optional.of(
                                new ShareInviteRepositoryImpl.InviteWithDeadline(
                                        INVITE, Instant.now().minusSeconds(1))),
                        Optional.of(
                                new ShareInviteRepositoryImpl.InviteWithDeadline(INVITE, null)));
        assertThat(cache.findByRef(REF)).contains(INVITE);
        assertThat(cache.findByRef(REF)).contains(INVITE);
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void missingReferencesAreNotNegativeCached() {
        when(source.findByRefWithDeadline(REF)).thenReturn(Optional.empty());
        assertThat(cache.findByRef(REF)).isEmpty();
        assertThat(cache.findByRef(REF)).isEmpty();
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void malformedUnsupportedForeignAndExpiredPayloadsFallBackToPostgres() {
        Instant future = Instant.now().plusSeconds(60);
        ShareInvite foreign =
                new ShareInvite(
                        UUID.randomUUID(),
                        "BBBBBBBBBBBBBBBBBBBBBB",
                        INVITE.dealId(),
                        INVITE.sharerUserId());
        String[] unusable = {
            "not-json",
            "null",
            json.writeValueAsString(new RedisCachedShareInviteRepository.Entry(1, null, future)),
            json.writeValueAsString(new RedisCachedShareInviteRepository.Entry(1, INVITE, null)),
            json.writeValueAsString(new RedisCachedShareInviteRepository.Entry(2, INVITE, future)),
            json.writeValueAsString(new RedisCachedShareInviteRepository.Entry(1, foreign, future)),
            json.writeValueAsString(
                    new RedisCachedShareInviteRepository.Entry(
                            1, INVITE, Instant.now().minusSeconds(1)))
        };
        when(source.findByRefWithDeadline(REF))
                .thenReturn(
                        Optional.of(
                                new ShareInviteRepositoryImpl.InviteWithDeadline(INVITE, future)));
        when(values.get(KEY))
                .thenReturn(unusable[0], Arrays.copyOfRange(unusable, 1, unusable.length));
        for (String ignored : unusable) {
            assertThat(cache.findByRef(REF)).contains(INVITE);
        }
        verify(source, times(unusable.length)).findByRefWithDeadline(REF);
    }

    @Test
    void readOutageFallsBackWithoutWaitingForASecondRedisWrite() {
        when(values.get(KEY)).thenThrow(new RedisConnectionFailureException("private connection"));
        when(source.findByRefWithDeadline(REF))
                .thenReturn(
                        Optional.of(
                                new ShareInviteRepositoryImpl.InviteWithDeadline(
                                        INVITE, Instant.now().plusSeconds(60))));
        assertThat(cache.findByRef(REF)).contains(INVITE);
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void writeOutageDoesNotDiscardThePostgresResult() {
        when(source.findByRefWithDeadline(REF))
                .thenReturn(
                        Optional.of(
                                new ShareInviteRepositoryImpl.InviteWithDeadline(
                                        INVITE, Instant.now().plusSeconds(60))));
        doThrow(new RedisConnectionFailureException("private connection"))
                .when(values)
                .set(eq(KEY), anyString(), any(Duration.class));
        assertThat(cache.findByRef(REF)).contains(INVITE);
    }

    @Test
    void malformedReferenceAndOtherRepositoryOperationsRemainUnchanged() {
        when(source.findByRef("bad")).thenReturn(Optional.empty());
        assertThat(cache.findByRef("bad")).isEmpty();
        verifyNoInteractions(values);

        when(source.findByDealAndSharer(INVITE.dealId(), INVITE.sharerUserId()))
                .thenReturn(Optional.of(INVITE));
        when(source.insertIfAbsent(INVITE)).thenReturn(true);
        assertThat(cache.findByDealAndSharer(INVITE.dealId(), INVITE.sharerUserId()))
                .contains(INVITE);
        assertThat(cache.insertIfAbsent(INVITE)).isTrue();
    }
}
