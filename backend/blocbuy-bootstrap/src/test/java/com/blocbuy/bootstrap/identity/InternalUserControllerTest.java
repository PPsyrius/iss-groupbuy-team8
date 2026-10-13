package com.blocbuy.bootstrap.identity;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import com.blocbuy.common.web.GlobalExceptionHandler;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.identity.web.internal.InternalUserController;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Identity side of the /internal/users wire contract (#468): every answer is the standard envelope,
 * and refusals carry the identity error code the commerce adapter maps back.
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
}
