package com.blocbuy.identity.web;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.web.Result;
import com.blocbuy.identity.service.OAuthLoginAppService;
import com.blocbuy.identity.service.OAuthLoginAppService.GoogleAuthorizeUrl;
import com.blocbuy.identity.service.OAuthLoginAppService.OAuthLoginResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
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

    /**
     * Binds the one-time OAuth state to the initiating browser; HttpOnly + SameSite=Lax so it rides
     * the provider redirect but is unreadable from script and not sent cross-site elsewhere.
     */
    private static final String OAUTH_STATE_COOKIE = "blocbuy-oauth-state";

    /**
     * Starts the Google consent flow: 302 to the provider authorization URL, plus the browser-
     * session binding cookie. Public path. Envelope-exempt: a redirect is a protocol handshake
     * (Location + Set-Cookie headers), not a JSON resource response.
     */
    @Operation(summary = "Redirect to Google login")
    @GetMapping("/oauth2/google")
    public ResponseEntity<Void> googleAuthorize(HttpServletRequest request) {
        GoogleAuthorizeUrl authorize = oAuthLoginAppService.createGoogleAuthorizeUrl();
        ResponseCookie bindingCookie =
                ResponseCookie.from(OAUTH_STATE_COOKIE, authorize.stateBinding())
                        .httpOnly(true)
                        .sameSite("Lax")
                        // context-path (e.g. /api on dev) is part of the browser-visible path;
                        // without it the cookie is never sent back to the callback
                        .path(request.getContextPath() + ApiConstants.API_V1 + "/auth")
                        .build();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authorize.url()))
                .header(HttpHeaders.SET_COOKIE, bindingCookie.toString())
                .build();
    }

    /**
     * Google callback: verifies code + one-time state against the browser-session binding cookie,
     * resolves or registers the local account, then opens the Sa-Token session and returns the
     * token in the body. Public path.
     */
    @Operation(summary = "Google OAuth2 callback — issues the session token")
    @GetMapping("/oauth2/google/callback")
    public Result<LoginResponse> googleCallback(
            @RequestParam @NotBlank String code,
            @RequestParam @NotBlank String state,
            @CookieValue(name = OAUTH_STATE_COOKIE, required = false) String stateBinding) {
        OAuthLoginResult result = oAuthLoginAppService.loginWithGoogle(code, state, stateBinding);
        StpUtil.login(result.userId());
        return Result.ok(
                new LoginResponse(
                        StpUtil.getTokenValue(),
                        result.userId().toString(),
                        result.nickname(),
                        result.avatarUrl()));
    }

    /** Current session; unauthenticated callers get 401 from the Sa-Token interceptor. */
    @Operation(summary = "Current logged-in user id")
    @GetMapping("/me")
    public Result<MeResponse> me() {
        return Result.ok(new MeResponse(StpUtil.getLoginIdAsString()));
    }

    /** Logout: invalidates the current token. */
    @Operation(summary = "Logout")
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.ok();
    }

    /** Session view; never contains tokens or credentials. */
    public record MeResponse(String loginId) {}

    /** Login result; the token travels in the body (TLS), never in URLs or logs. */
    public record LoginResponse(String token, String userId, String nickname, String avatarUrl) {}
}
