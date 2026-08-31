package com.blocbuy.identity.web;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication entry points (Sa-Token). Protocol adaptation only — credential verification
 * belongs to the identity application service.
 * TODO(TEAM8-identity): POST /auth/login and /auth/register land with the user tables (V2).
 */
@Tag(name = "auth", description = "Login, logout, current session")
@RestController
@RequestMapping(ApiConstants.API_V1 + "/auth")
public class AuthController {

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
}
