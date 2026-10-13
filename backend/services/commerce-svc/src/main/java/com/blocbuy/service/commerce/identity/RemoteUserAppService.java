package com.blocbuy.service.commerce.identity;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import com.blocbuy.common.web.Result;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.exception.IdentityUnavailableException;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.identity.web.internal.InternalUserController;
import com.blocbuy.identity.web.internal.InternalUserController.PublicNicknameView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link UserAppService} over HTTP to identity-svc's {@code /internal/users} (backend ADR 0008,
 * #468). Identity's two business refusals become the same {@link BizException}s the local
 * implementation throws, recognised by their error code and status together, so a 404 from a
 * missing route is never read as "user not found". Everything else, timeouts included, is an {@link
 * IdentityUnavailableException}: the caller fails closed with 500, and the Stripe webhook rolls
 * back so the provider redelivers instead of the buyer being refunded as inactive. No retry, no
 * cache. Nothing is logged here; the global handler logs the failure once.
 */
public class RemoteUserAppService implements UserAppService {

    /**
     * Identity's business refusals of {@code requireActive}, as the local implementation throws
     * them.
     */
    private static final List<IdentityErrorCode> REFUSALS =
            List.of(IdentityErrorCode.USER_NOT_FOUND, IdentityErrorCode.ACCOUNT_DISABLED);

    private static final ParameterizedTypeReference<Result<Void>> NO_DATA =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Result<PublicNicknameView>> NICKNAME =
            new ParameterizedTypeReference<>() {};

    private final RestClient identity;

    @SuppressFBWarnings("EI_EXPOSE_REP2") // Spring-built client, shared and thread-safe by design.
    public RemoteUserAppService(RestClient identity) {
        this.identity = identity;
    }

    @Override
    public void requireActive(UUID userId) {
        Reply<Void> reply =
                exchange(
                        "requireActive",
                        InternalUserController.PATH + InternalUserController.ACTIVE,
                        userId,
                        NO_DATA);
        if (reply.isSuccess()) {
            return;
        }
        for (IdentityErrorCode refusal : REFUSALS) {
            if (reply.refuses(refusal)) {
                throw new BizException(refusal);
            }
        }
        throw unexpected("requireActive", reply);
    }

    /** Only identity's own {@code AuthController} reads the session profile (ArchUnit-enforced). */
    @Override
    public CurrentUser currentUser(UUID userId) {
        throw new UnsupportedOperationException("currentUser is served by identity-svc only");
    }

    @Override
    public Optional<String> publicNickname(UUID userId) {
        Reply<PublicNicknameView> reply =
                exchange(
                        "publicNickname",
                        InternalUserController.PATH + InternalUserController.PUBLIC_NICKNAME,
                        userId,
                        NICKNAME);
        if (!reply.isSuccess() || reply.body().data() == null) {
            throw unexpected("publicNickname", reply);
        }
        return Optional.ofNullable(reply.body().data().nickname());
    }

    /**
     * One GET. The body is read only for the statuses identity answers on purpose; any other
     * status, an unreadable body and every transport failure end here as identity being
     * unavailable.
     */
    private <T> Reply<T> exchange(
            String operation,
            String path,
            UUID userId,
            ParameterizedTypeReference<Result<T>> type) {
        long started = System.nanoTime();
        try {
            return identity.get()
                    .uri(path, userId)
                    .exchange(
                            (request, response) -> {
                                int status = response.getStatusCode().value();
                                Result<T> body =
                                        answersOnPurpose(status) ? response.bodyTo(type) : null;
                                return new Reply<>(status, body, elapsedMillis(started));
                            });
        } catch (RestClientException | HttpMessageConversionException failure) {
            throw new IdentityUnavailableException(
                    "identity "
                            + operation
                            + " failed after "
                            + elapsedMillis(started)
                            + " ms: "
                            + failure.getClass().getSimpleName(),
                    failure);
        }
    }

    private static boolean answersOnPurpose(int status) {
        return status == HttpStatus.OK.value()
                || REFUSALS.stream().anyMatch(refusal -> refusal.httpStatus() == status);
    }

    private static IdentityUnavailableException unexpected(String operation, Reply<?> reply) {
        return new IdentityUnavailableException(
                "identity "
                        + operation
                        + " answered "
                        + reply.status()
                        + (reply.body() == null ? "" : " " + reply.body().code())
                        + " after "
                        + reply.elapsedMillis()
                        + " ms",
                null);
    }

    private static long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private record Reply<T>(int status, Result<T> body, long elapsedMillis) {

        boolean isSuccess() {
            return answers(HttpStatus.OK.value(), CommonErrorCode.SUCCESS.code());
        }

        boolean refuses(IdentityErrorCode refusal) {
            return answers(refusal.httpStatus(), refusal.code());
        }

        private boolean answers(int expectedStatus, String expectedCode) {
            return status == expectedStatus && body != null && expectedCode.equals(body.code());
        }
    }
}
