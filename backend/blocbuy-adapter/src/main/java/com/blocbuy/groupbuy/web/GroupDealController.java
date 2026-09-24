package com.blocbuy.groupbuy.web;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import com.blocbuy.common.web.Result;
import com.blocbuy.groupbuy.query.ShareInviteView;
import com.blocbuy.groupbuy.service.ShareInviteAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GroupDeal resource controller. Protocol adaptation only: parse/validate requests, convert to
 * commands/queries, call the application service. No business transactions, no mapper calls, no
 * direct cache manipulation. Endpoints land with their use cases.
 */
@Tag(name = "group-deals", description = "GroupDeal resource")
@SecurityRequirement(name = "blocbuyToken")
@ApiResponse(responseCode = "200", description = "Success in Result envelope")
@ApiResponse(responseCode = "400", description = "Invalid request")
@ApiResponse(responseCode = "401", description = "Login required")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.API_V1 + "/group-deals")
public class GroupDealController {

    private final ShareInviteAppService invites;

    /**
     * UC7: the caller's invite into a team they belong to. Naturally idempotent — one invite per
     * team and member — so a repeat returns the same reference and no {@code Idempotency-Key} is
     * needed. A non-member gets the same 404 as a missing team.
     */
    @Operation(
            operationId = "createShareInvite",
            summary = "Mint or reuse my invite reference for a team I belong to")
    @ApiResponse(responseCode = "403", description = "Wrong X-BlocBuy-Request marker")
    @ApiResponse(responseCode = "404", description = "No such team, or caller is not a member")
    @ApiResponse(responseCode = "409", description = "Team no longer accepts members")
    @PostMapping("/{dealId}/invites")
    public Result<ShareInviteResponse> createInvite(
            @RequestHeader("X-BlocBuy-Request") String csrf,
            @PathVariable UUID dealId,
            HttpServletResponse response) {
        // JSON plus a non-simple header requires preflight cross-origin; no CORS is enabled.
        if (!"invite".equals(csrf)) {
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
        StpUtil.checkLogin();
        response.setHeader("Cache-Control", "no-store");
        UUID member = UUID.fromString(StpUtil.getLoginIdAsString());
        ShareInviteView view = invites.mint(member, dealId);
        return Result.ok(
                new ShareInviteResponse(
                        view.ref(), view.dealId(), view.productId(), view.expiresAt()));
    }

    /** A member's invite; the frontend builds the shareable link from these fields. */
    public record ShareInviteResponse(
            @Schema(description = "Opaque invite reference; send it back as `ref` on a JOIN intent")
                    String ref,
            UUID dealId,
            UUID productId,
            @Schema(description = "The team's deadline; the invite stops attributing after it")
                    Instant expiresAt) {}
}
