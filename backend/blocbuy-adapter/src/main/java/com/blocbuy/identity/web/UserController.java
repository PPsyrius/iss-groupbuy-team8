package com.blocbuy.identity.web;

import com.blocbuy.common.constant.ApiConstants;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User resource controller. Protocol adaptation only: parse/validate requests, convert to
 * commands/queries, call the application service. No business transactions, no mapper calls, no
 * direct cache manipulation. Endpoints land with their use cases. TODO(TEAM8-identity): add
 * endpoints; write endpoints define idempotency ({@code Idempotency-Key}).
 */
@Tag(name = "users", description = "User resource")
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/users")
public class UserController {}
