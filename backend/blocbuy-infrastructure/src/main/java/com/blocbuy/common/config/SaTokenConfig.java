package com.blocbuy.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
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
    ApiConstants.API_V1 + "/auth/login",
    ApiConstants.API_V1 + "/auth/register",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/actuator/health/**",
    "/actuator/prometheus"
  };

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(new SaInterceptor(handler -> SaRouter.match("/**", StpUtil::checkLogin)))
        .addPathPatterns(ApiConstants.API_V1 + "/**")
        .excludePathPatterns(PUBLIC_PATHS);
  }
}
