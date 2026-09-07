package com.blocbuy.identity.web;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.identity.service.OAuthLoginAppService;
import com.blocbuy.identity.service.OAuthLoginAppService.OAuthLoginResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication entry points (Sa-Token). Protocol adaptation only — credential verification
 * belongs to the identity application service. Google OAuth2 is the sole login channel: the Sa-
 * Token session opens with the local user id only after the application service has verified the
 * provider identity; provider tokens never appear here.
 */
@Tag(name = "auth", description = "Google OAuth2 login, logout, current session")
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OAuthLoginAppService oAuthLoginAppService;

    /** Starts the Google consent flow: 302 to the provider authorization URL. Public path. */
    @Operation(summary = "Redirect to Google login")
    @GetMapping("/oauth2/google")
    public ResponseEntity<Void> googleAuthorize() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(oAuthLoginAppService.createGoogleAuthorizeUrl()))
                .build();
    }

    /**
     * Google callback: verifies code + one-time state, resolves or registers the local account,
     * then opens the Sa-Token session and returns the token in the body. Public path.
     */
    @Operation(summary = "Google OAuth2 callback — issues the session token")
    @GetMapping("/oauth2/google/callback")
    public LoginResponse googleCallback(
            @RequestParam @NotBlank String code, @RequestParam @NotBlank String state) {
        OAuthLoginResult result = oAuthLoginAppService.loginWithGoogle(code, state);
        StpUtil.login(result.userId());
        return new LoginResponse(
                StpUtil.getTokenValue(),
                result.userId().toString(),
                result.nickname(),
                result.avatarUrl());
    }

    /** Current session; unauthenticated callers get 401 from the Sa-Token interceptor. */
    @Operation(summary = "Current logged-in user id")
    @GetMapping("/me")
    public MeResponse me() {
        return new MeResponse(StpUtil.getLoginIdAsString());
    }

    /** Logout: invalidates the current token. */
    @Operation(summary = "Logout")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        StpUtil.logout();
        return ResponseEntity.noContent().build();
    }

    /** Session view; never contains tokens or credentials. */
    public record MeResponse(String loginId) {}

    /** Login result; the token travels in the body (TLS), never in URLs or logs. */
    public record LoginResponse(String token, String userId, String nickname, String avatarUrl) {}
}
