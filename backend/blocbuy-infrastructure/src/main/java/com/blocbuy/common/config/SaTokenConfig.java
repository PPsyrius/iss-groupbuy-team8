package com.blocbuy.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.ApiConstants;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token wiring: one token domain (D7), sessions in Redis (D6 via sa-token-redis-jackson
 * auto-config). The interceptor enforces login on all {@code /api/v1/**} except explicit public
 * paths; object-level ownership checks still belong to application services (IDOR prevention).
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /** Paths that never require authentication. */
    private static final String[] PUBLIC_PATHS = {
        // Distributed internal endpoints rely on peer-only network isolation (ADR 0008).
        "/internal/**",
        // Google OAuth2 is the sole login channel (redirect start + provider callback)
        ApiConstants.API_V1 + "/auth/oauth2/**",
        // provider callbacks authenticate by signature verification, not by session
        ApiConstants.API_V1 + "/webhooks/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/actuator/health/**",
        "/actuator/prometheus"
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(
                        new SaInterceptor(
                                handler -> {
                                    // anonymous catalog browse and invite resolve: GET only;
                                    // every other method/path under /api/v1 requires it
                                    if (SaRouter.isMatchCurrURI(ANONYMOUS_GET_PATHS)
                                            && SaRouter.isMatchCurrMethod(
                                                    new SaHttpMethod[] {SaHttpMethod.GET})) {
                                        return;
                                    }
                                    StpUtil.checkLogin();
                                }))
                .addPathPatterns(ApiConstants.API_V1 + "/**")
                .excludePathPatterns(PUBLIC_PATHS);
    }

    /**
     * Read-only paths open without a session; writes on the same resources stay protected. Invite
     * links are resolved before sign-in (#201).
     */
    private static final String[] ANONYMOUS_GET_PATHS = {
        ApiConstants.API_V1 + "/products/**", ApiConstants.API_V1 + "/invites/*"
    };
}
