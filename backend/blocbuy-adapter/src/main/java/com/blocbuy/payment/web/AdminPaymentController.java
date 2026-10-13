package com.blocbuy.payment.web;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.common.model.RoleCode;
import com.blocbuy.common.web.Result;
import com.blocbuy.payment.model.PaymentAttention;
import com.blocbuy.payment.service.AdminPaymentAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administrator-only financial visibility; no refund/retry/dismiss operation. */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.API_V1 + "/admin/payments")
@SecurityRequirement(name = "blocbuyToken")
@Tag(name = "admin-payments", description = "Read-only payments needing attention")
@ApiResponse(responseCode = "200", description = "Paged Result envelope")
@ApiResponse(responseCode = "400", description = "Invalid pagination")
@ApiResponse(responseCode = "401", description = "Login required")
@ApiResponse(
        responseCode = "403",
        description = "Admin role and payment:read required; active account only")
public class AdminPaymentController {
    private final AdminPaymentAppService payments;

    /** Only terminal compensation receipts with an outstanding refund are listed. */
    @GetMapping("/attention")
    @Operation(
            operationId = "pageAdminPaymentsNeedingAttention",
            summary = "Page payments needing attention",
            description =
                    "Successfully charged payments with a non-deleted compensation_required "
                            + "participation receipt and no succeeded non-deleted refund. Open review cases alone "
                            + "are excluded. Newest receipt first, payment UUID descending tie-breaker. "
                            + "page starts at 1; size is 1-100 (default 20). Read-only snapshot, no provider calls. "
                            + "Missing refund is pending; processing and review retain their stored status. "
                            + "Buyer nickname only; unknown/deleted buyers use Unknown buyer. "
                            + "dealId/dealNo may be absent when no bloc was created or its record is missing.")
    public Result<PageResult<PaymentAttentionResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        StpUtil.checkLogin();
        StpUtil.checkRole(RoleCode.ADMIN);
        StpUtil.checkPermission("payment:read");
        var result =
                payments.pageAttention(
                        new PageQuery(page, size), UUID.fromString(StpUtil.getLoginIdAsString()));
        return Result.ok(
                new PageResult<>(
                        result.items().stream().map(PaymentAttentionResponse::from).toList(),
                        result.total(),
                        result.page(),
                        result.size(),
                        result.nextCursor()));
    }

    /**
     * Stable protocol fields; money uses exact decimal strings, never a JSON floating-point value.
     */
    public record PaymentAttentionResponse(
            UUID paymentId,
            String buyerNickname,
            @Schema(nullable = true) UUID dealId,
            @Schema(nullable = true) String dealNo,
            String reasonCode,
            @Schema(
                            example = "12.00",
                            description = "Amount charged in major units as a decimal string")
                    String amount,
            String currency,
            @Schema(allowableValues = {"pending", "processing", "failed", "review"})
                    String refundStatus,
            @Schema(description = "Time the participation compensation result was recorded")
                    Instant occurredAt) {
        static PaymentAttentionResponse from(PaymentAttention item) {
            return new PaymentAttentionResponse(
                    item.paymentId(),
                    item.buyerNickname(),
                    item.dealId(),
                    item.dealNo(),
                    item.reasonCode(),
                    item.amount().toPlainString(),
                    item.currency(),
                    item.refundStatus(),
                    item.occurredAt());
        }
    }
}
