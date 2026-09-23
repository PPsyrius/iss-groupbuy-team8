package com.blocbuy.order.web;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.common.web.Result;
import com.blocbuy.order.query.OrderSummaryView;
import com.blocbuy.order.service.OrderAppService;
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
 * Order resource controller. Protocol adaptation only: parse/validate requests, convert to
 * commands/queries, call the application service. No business transactions, no mapper calls, no
 * direct cache manipulation. The buyer id always comes from the Sa-Token session, never the wire;
 * order creation and confirmation land with the paid-participation and team-event seams.
 */
@Tag(name = "orders", description = "Buyer orders of record")
@SecurityRequirement(name = "blocbuyToken")
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/orders")
@RequiredArgsConstructor
@ApiResponse(responseCode = "200", description = "Success in Result envelope")
@ApiResponse(responseCode = "400", description = "Invalid request")
@ApiResponse(responseCode = "401", description = "Login required")
public class OrderController {

    private final OrderAppService orders;

    /** Owner-scoped My Orders page, newest first; anonymous access is rejected by Sa-Token. */
    @Operation(summary = "List my orders with product snapshot")
    @GetMapping("/me")
    public Result<PageResult<OrderSummaryResponse>> myOrders(
            @RequestParam(required = false) @Min(1) Integer page,
            @RequestParam(required = false) @Min(1) @Max(PageQuery.MAX_SIZE) Integer size,
            HttpServletResponse response) {
        StpUtil.checkLogin();
        response.setHeader("Cache-Control", "no-store");
        UUID buyer = UUID.fromString(StpUtil.getLoginIdAsString());
        PageResult<OrderSummaryView> result = orders.pageMyOrders(PageQuery.of(page, size), buyer);
        List<OrderSummaryResponse> items =
                result.items().stream().map(OrderController::toResponse).toList();
        return Result.ok(
                new PageResult<>(
                        items, result.total(), result.page(), result.size(), result.nextCursor()));
    }

    private static OrderSummaryResponse toResponse(OrderSummaryView view) {
        return new OrderSummaryResponse(
                view.orderId(),
                view.orderNo(),
                view.dealId(),
                view.productId(),
                view.productTitle(),
                view.productImage(),
                view.quantity(),
                view.unitPrice().toPlainString(),
                view.totalAmount().toPlainString(),
                view.currency(),
                view.status(),
                view.paidTime(),
                view.cancelTime(),
                view.createTime());
    }

    /** One buyer order for the My Orders list; no payment reference, no receiver PII. */
    public record OrderSummaryResponse(
            UUID orderId,
            String orderNo,
            UUID dealId,
            UUID productId,
            String productTitle,
            @Schema(nullable = true) String productImage,
            int quantity,
            @Schema(description = "Exact decimal major units", example = "12.00") String unitPrice,
            @Schema(description = "Exact decimal major units", example = "12.00")
                    String totalAmount,
            String currency,
            String status,
            Instant paidTime,
            @Schema(nullable = true) Instant cancelTime,
            Instant createTime) {}
}
