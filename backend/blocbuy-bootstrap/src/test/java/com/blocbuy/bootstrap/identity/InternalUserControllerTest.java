package com.blocbuy.bootstrap.identity;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import com.blocbuy.common.web.GlobalExceptionHandler;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.identity.web.internal.InternalUserController;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Identity side of the /internal/users wire contract (#468, #469): every answer is the standard
 * envelope, and refusals carry the identity error code the commerce adapter maps back.
 */
class InternalUserControllerTest {

    private static final UUID USER = UUID.fromString("a0000000-0000-7000-8000-000000000001");

    private final UserAppService users = mock(UserAppService.class);
    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(new InternalUserController(users))
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();

    @Test
    void anActiveAccountAnswersSuccess() throws Exception {
        mvc.perform(get("/internal/users/{id}/active", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.SUCCESS.code()));
    }

    @Test
    void aMissingAccountAnswersTheIdentityNotFoundCode() throws Exception {
        doThrow(new BizException(IdentityErrorCode.USER_NOT_FOUND)).when(users).requireActive(USER);

        mvc.perform(get("/internal/users/{id}/active", USER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(IdentityErrorCode.USER_NOT_FOUND.code()));
    }

    @Test
    void aDisabledAccountAnswersTheIdentityDisabledCode() throws Exception {
        doThrow(new BizException(IdentityErrorCode.ACCOUNT_DISABLED))
                .when(users)
                .requireActive(USER);

        mvc.perform(get("/internal/users/{id}/active", USER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(IdentityErrorCode.ACCOUNT_DISABLED.code()));
    }

    @Test
    void aMalformedIdIsABadRequest() throws Exception {
        mvc.perform(get("/internal/users/{id}/active", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.BAD_REQUEST.code()));
    }

    @Test
    void aShowableNicknameIsReturned() throws Exception {
        when(users.publicNickname(USER)).thenReturn(Optional.of("Tester"));

        mvc.perform(get("/internal/users/{id}/public-nickname", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.SUCCESS.code()))
                .andExpect(jsonPath("$.data.nickname").value("Tester"));
    }

    @Test
    void noShowableNicknameIsStillASuccessWithANullNickname() throws Exception {
        when(users.publicNickname(USER)).thenReturn(Optional.empty());

        mvc.perform(get("/internal/users/{id}/public-nickname", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.SUCCESS.code()))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.nickname").doesNotExist());
    }

    private static final UUID OTHER = UUID.fromString("a0000000-0000-7000-8000-000000000002");
    private static final String BATCH = "/internal/users/public-nicknames";

    @Test
    void theBatchLookupAnswersOnlyShowableNicknames() throws Exception {
        when(users.publicNicknames(Set.of(USER, OTHER))).thenReturn(Map.of(USER, "Tester"));

        mvc.perform(batch(ids(USER, OTHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.SUCCESS.code()))
                .andExpect(jsonPath("$.data.nicknames." + USER).value("Tester"))
                .andExpect(jsonPath("$.data.nicknames." + OTHER).doesNotExist());
    }

    @Test
    void theBatchLookupAcceptsTheLargestPage() throws Exception {
        UUID[] hundred =
                IntStream.range(0, 100)
                        .mapToObj(InternalUserControllerTest::uuid)
                        .toArray(UUID[]::new);
        when(users.publicNicknames(any())).thenReturn(Map.of());

        mvc.perform(batch(ids(hundred)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nicknames").isEmpty());
    }

    @Test
    void theBatchLookupRejectsMoreThanTheLargestPage() throws Exception {
        UUID[] tooMany =
                IntStream.range(0, 101)
                        .mapToObj(InternalUserControllerTest::uuid)
                        .toArray(UUID[]::new);

        assertBadBatch(ids(tooMany));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"userIds\":[]}",
                "{\"userIds\":null}",
                "{}",
                "{\"userIds\":[null]}",
                "{\"userIds\":[\"not-a-uuid\"]}",
                "not json"
            })
    void aMalformedBatchIsABadRequest(String body) throws Exception {
        assertBadBatch(body);
    }

    @Test
    void aBatchWithoutABodyIsABadRequest() throws Exception {
        mvc.perform(post(BATCH).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(startsWith("COMMON_400_")));
        verify(users, never()).publicNicknames(any());
    }

    private void assertBadBatch(String body) throws Exception {
        mvc.perform(batch(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(startsWith("COMMON_400_")));
        verify(users, never()).publicNicknames(any());
    }

    private static RequestBuilder batch(String body) {
        return post(BATCH).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String ids(UUID... ids) {
        return Set.of(ids).stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(",", "{\"userIds\":[", "]}"));
    }

    private static UUID uuid(int i) {
        return UUID.fromString(String.format("a0000000-0000-7000-8000-%012d", i));
    }
}
