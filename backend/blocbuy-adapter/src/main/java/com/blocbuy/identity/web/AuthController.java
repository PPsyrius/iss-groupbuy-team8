package com.blocbuy.identity.web;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.web.Result;
import com.blocbuy.identity.service.OAuthLoginAppService;
import com.blocbuy.identity.service.OAuthLoginAppService.GoogleAuthorizeUrl;
import com.blocbuy.identity.service.OAuthLoginAppService.OAuthLoginResult;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.identity.service.UserAppService.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
 *
 * <p>The OAuth endpoints are browser handshakes, not JSON resources: both end in a 302 (to Google,
 * then back to the frontend via {@link AuthWebProperties}), and the session token rides only the
 * HttpOnly cookie Sa-Token sets on login — it never appears in a body, URL, or log.
 */
@Tag(name = "auth", description = "Google OAuth2 login, logout, current session")
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/auth")
@RequiredArgsConstructor
@EnableConfigurationProperties(AuthWebProperties.class)
public class AuthController {

    private final OAuthLoginAppService oAuthLoginAppService;
    private final UserAppService userAppService;
    private final AuthWebProperties authWebProperties;

    /**
     * Binds the one-time OAuth state to the initiating browser; HttpOnly + SameSite=Lax so it rides
     * the provider redirect but is unreadable from script and not sent cross-site elsewhere.
     */
    private static final String OAUTH_STATE_COOKIE = "blocbuy-oauth-state";

    /**
     * Starts the Google consent flow: 302 to the provider authorization URL, plus the browser-
     * session binding cookie. Public path. Envelope-exempt: a redirect is a protocol handshake
     * (Location + Set-Cookie headers), not a JSON resource response. A provider-side failure still
     * honors the browser contract — the user lands on the frontend failure page.
     */
    @Operation(summary = "Redirect to Google login")
    @GetMapping("/oauth2/google")
    public ResponseEntity<Void> googleAuthorize(HttpServletRequest request) {
        GoogleAuthorizeUrl authorize;
        try {
            authorize = oAuthLoginAppService.createGoogleAuthorizeUrl();
        } catch (BizException ex) {
            return redirect(authWebProperties.failureRedirectUri(ex.errorCode().code()));
        }
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
     * resolves or registers the local account, opens the Sa-Token session, then 302s back to the
     * configured frontend page. Envelope-exempt protocol handshake; failures redirect to the
     * frontend failure page with the error code instead of leaving the user on a raw JSON error.
     * The try/catch below is deliberately scoped to the handshake call — it maps a browser-flow
     * failure to the redirect contract, it never assembles an error envelope (that stays with the
     * global handler for the JSON endpoints in this controller).
     */
    @Operation(summary = "Google OAuth2 callback — opens the session, redirects to the frontend")
    @GetMapping("/oauth2/google/callback")
    public ResponseEntity<Void> googleCallback(
            @RequestParam @NotBlank String code,
            @RequestParam @NotBlank String state,
            @CookieValue(name = OAUTH_STATE_COOKIE, required = false) String stateBinding) {
        OAuthLoginResult result;
        try {
            result = oAuthLoginAppService.loginWithGoogle(code, state, stateBinding);
        } catch (BizException ex) {
            return redirect(authWebProperties.failureRedirectUri(ex.errorCode().code()));
        }
        StpUtil.login(result.userId());
        return redirect(authWebProperties.successRedirectUri());
    }

    private static ResponseEntity<Void> redirect(String target) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    /** Current session profile; unauthenticated callers get 401 from the Sa-Token interceptor. */
    @Operation(summary = "Current logged-in user")
    // method-level, not class-level: the /oauth2/** endpoints on this controller are public paths
    @SecurityRequirement(name = "blocbuyToken")
    @GetMapping("/me")
    public Result<MeResponse> me() {
        CurrentUser user =
                userAppService.currentUser(UUID.fromString(StpUtil.getLoginIdAsString()));
        return Result.ok(
                new MeResponse(user.userId().toString(), user.nickname(), user.avatarUrl()));
    }

    /** Logout: invalidates the current token. */
    @Operation(summary = "Logout")
    @SecurityRequirement(name = "blocbuyToken")
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.ok();
    }

    /** Session profile view; never contains tokens or credentials. */
    public record MeResponse(String userId, String nickname, String avatarUrl) {}
}
