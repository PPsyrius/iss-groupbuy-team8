package com.blocbuy.groupbuy.web;

import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.web.ClientAddressResolver;
import com.blocbuy.common.web.Result;
import com.blocbuy.groupbuy.model.ShareInvite;
import com.blocbuy.groupbuy.query.ResolvedInviteView;
import com.blocbuy.groupbuy.service.ShareInviteAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invite resource (UC7). Anonymous: a buyer opens an invite link before signing in, so the Sa-Token
 * interceptor exempts GET on this path only. Protocol adaptation only; the rate limit and the
 * lookup live in the application service.
 */
@Tag(name = "invites", description = "Invite links shared by team members")
@ApiResponse(responseCode = "200", description = "Success in Result envelope")
@ApiResponse(responseCode = "400", description = "Malformed invite reference")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.API_V1 + "/invites")
public class InviteController {

    private final ShareInviteAppService invites;
    private final ClientAddressResolver clientAddresses;

    /**
     * Resolves an invite reference for its landing page. Never cached: the header is set before the
     * lookup, so error responses carry it too.
     */
    @Operation(
            operationId = "resolveInvite",
            summary = "Resolve an invite reference to its team and the sharer's nickname",
            description =
                    "Anonymous. Any stored invite resolves, whatever its team's status, so the"
                            + " landing page can also name the sharer of a full or ended team."
                            + " Limited to 60 requests per minute per client address.")
    @ApiResponse(responseCode = "404", description = "Unknown or deleted invite reference")
    @ApiResponse(responseCode = "429", description = "Too many requests from this client address")
    @GetMapping("/{ref}")
    public Result<InviteResponse> resolve(
            @PathVariable @Pattern(regexp = ShareInvite.REF_PATTERN) String ref,
            HttpServletRequest request,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        ResolvedInviteView view = invites.resolve(ref, clientAddresses.resolve(request));
        return Result.ok(
                new InviteResponse(
                        view.dealId(), view.productId(), view.sharerNickname(), view.expiresAt()));
    }

    /** What an invite landing page may show before sign-in; no user id or contact detail. */
    public record InviteResponse(
            UUID dealId,
            UUID productId,
            @Schema(
                            nullable = true,
                            description =
                                    "The sharer's nickname for display; null when there is none"
                                            + " to show. Unverified display text, never an"
                                            + " identifier")
                    String sharerNickname,
            @Schema(description = "The team's deadline") Instant expiresAt) {}
}
