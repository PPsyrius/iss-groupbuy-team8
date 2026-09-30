package com.blocbuy.identity.web;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import com.blocbuy.common.web.Result;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.service.OAuthLoginAppService;
import com.blocbuy.identity.service.OAuthLoginAppService.GoogleAuthorizeUrl;
import com.blocbuy.identity.service.OAuthLoginAppService.OAuthLoginResult;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.identity.service.UserAppService.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
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
    @ApiResponse(
            responseCode = "302",
            description =
                    "Always a redirect, never a JSON body. Location points to the Google consent"
                            + " page — or, if the authorization URL cannot be built, to the frontend"
                            + " /login?error={errorCode}. The Set-Cookie header carries the HttpOnly"
                            + " blocbuy-oauth-state cookie binding the one-time state to this browser.",
            headers = {
                @Header(
                        name = "Location",
                        description = "Google authorization URL, or the frontend failure page",
                        schema = @Schema(type = "string")),
                @Header(
                        name = "Set-Cookie",
                        description = "blocbuy-oauth-state browser-binding cookie",
                        schema = @Schema(type = "string"))
            })
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
                        // mirrors sa-token.cookie.secure: on for HTTPS deployments, off for the
                        // plain-HTTP local/docker/ec2 profiles until the CloudFront cutover (B3)
                        .secure(authWebProperties.stateCookieSecure())
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
     *
     * <p>A provider-side denial arrives as {@code ?error=access_denied&state=...} with no code, and
     * a malformed hit may lack params entirely — both still 302 to the failure page, because this
     * endpoint is only ever a browser navigation and must never answer with a raw 400 JSON body.
     * The denial path skips state verification: nothing is granted, so there is nothing to protect.
     */
    @Operation(summary = "Google OAuth2 callback — opens the session, redirects to the frontend")
    @ApiResponse(
            responseCode = "302",
            description =
                    "Always a redirect, never a JSON body. Success: Location points to the frontend"
                            + " /auth/callback and Set-Cookie carries the blocbuy-token Sa-Token session"
                            + " cookie (HttpOnly — issued on success only). Failure, malformed request,"
                            + " or consent denial (error=access_denied): Location points to"
                            + " /login?error={errorCode} and no session cookie is issued.",
            headers = {
                @Header(
                        name = "Location",
                        description = "Frontend success or failure landing URL",
                        schema = @Schema(type = "string")),
                @Header(
                        name = "Set-Cookie",
                        description = "blocbuy-token session cookie — success only",
                        schema = @Schema(type = "string"))
            })
    @GetMapping("/oauth2/google/callback")
    public ResponseEntity<Void> googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @CookieValue(name = OAUTH_STATE_COOKIE, required = false) String stateBinding) {
        if (error != null) {
            IdentityErrorCode denial =
                    "access_denied".equals(error)
                            ? IdentityErrorCode.OAUTH_ACCESS_DENIED
                            : IdentityErrorCode.OAUTH_PROVIDER_ERROR;
            return redirect(authWebProperties.failureRedirectUri(denial.code()));
        }
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return redirect(
                    authWebProperties.failureRedirectUri(CommonErrorCode.BAD_REQUEST.code()));
        }
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
                new MeResponse(
                        user.userId().toString(),
                        user.nickname(),
                        user.avatarUrl(),
                        StpUtil.getRoleList()));
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
    public record MeResponse(
            String userId,
            @Schema(nullable = true) String nickname,
            @Schema(nullable = true) String avatarUrl,
            List<String> roles) {}
}
