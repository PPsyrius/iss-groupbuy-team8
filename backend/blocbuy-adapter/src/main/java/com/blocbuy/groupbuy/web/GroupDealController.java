package com.blocbuy.groupbuy.web;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.common.web.Result;
import com.blocbuy.groupbuy.query.MyTeamView;
import com.blocbuy.groupbuy.service.GroupDealAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GroupDeal resource controller. Protocol adaptation only: parse/validate requests, convert to
 * commands/queries, call the application service. No business transactions, no mapper calls, no
 * direct cache manipulation. The buyer id always comes from the Sa-Token session, never the wire.
 * Write endpoints, when they land, define idempotency ({@code Idempotency-Key}).
 */
@Tag(name = "group-deals", description = "GroupDeal resource")
@SecurityRequirement(name = "blocbuyToken")
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/group-deals")
@RequiredArgsConstructor
@ApiResponse(responseCode = "200", description = "Success in Result envelope")
@ApiResponse(responseCode = "400", description = "Invalid request")
@ApiResponse(responseCode = "401", description = "Login required")
public class GroupDealController {

    private final GroupDealAppService groupDeals;

    /**
     * UC8 My Teams: owner-scoped page of teams the buyer leads or joined, newest participation
     * first; anonymous access is rejected by Sa-Token.
     */
    @Operation(
            summary = "List teams I lead or joined",
            description =
                    "Offset-paged, newest participation first. Each response reads one snapshot,"
                            + " so total always matches the rows it describes; a team joined"
                            + " between two page requests can shift rows across pages.")
    @GetMapping("/me")
    public Result<PageResult<MyTeamResponse>> myTeams(
            @RequestParam(required = false) @Min(1) Integer page,
            @RequestParam(required = false) @Min(1) @Max(PageQuery.MAX_SIZE) Integer size,
            HttpServletResponse response) {
        StpUtil.checkLogin();
        response.setHeader("Cache-Control", "no-store");
        UUID buyer = UUID.fromString(StpUtil.getLoginIdAsString());
        PageResult<MyTeamView> result = groupDeals.pageMyTeams(PageQuery.of(page, size), buyer);
        List<MyTeamResponse> items =
                result.items().stream().map(GroupDealController::toResponse).toList();
        return Result.ok(
                new PageResult<>(
                        items, result.total(), result.page(), result.size(), result.nextCursor()));
    }

    private static MyTeamResponse toResponse(MyTeamView view) {
        return new MyTeamResponse(
                view.dealId(),
                view.productId(),
                view.productTitle(),
                view.productImage(),
                view.dealPrice().toPlainString(),
                view.currency(),
                view.targetSize(),
                view.currentSize(),
                view.status(),
                view.expireTime(),
                view.successTime(),
                view.role(),
                view.joinTime(),
                view.orderId());
    }

    /** One team in the My Teams list; committed status, so it always agrees with the order. */
    public record MyTeamResponse(
            UUID dealId,
            UUID productId,
            @Schema(
                            nullable = true,
                            description = "Current catalog title; null if the product was deleted")
                    String productTitle,
            @Schema(nullable = true) String productImage,
            @Schema(description = "Exact decimal major units", example = "12.00") String dealPrice,
            String currency,
            int targetSize,
            int currentSize,
            @Schema(description = "open, succeeded or expired") String status,
            Instant expireTime,
            @Schema(nullable = true) Instant successTime,
            @Schema(description = "leader or member") String role,
            Instant joinTime,
            @Schema(nullable = true) UUID orderId) {}
}
