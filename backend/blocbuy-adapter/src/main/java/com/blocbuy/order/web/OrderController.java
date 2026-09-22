package com.blocbuy.order.web;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.common.web.Result;
import com.blocbuy.order.exception.OrderErrorCode;
import com.blocbuy.order.query.OrderSummaryView;
import com.blocbuy.order.service.OrderAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
            @RequestParam(required = false) @Min(1) @Max(PageQuery.MAX_SIZE) Integer size) {
        StpUtil.checkLogin();
        UUID buyer = UUID.fromString(StpUtil.getLoginIdAsString());
        PageResult<OrderSummaryView> result = orders.pageMyOrders(PageQuery.of(page, size), buyer);
        List<OrderSummaryResponse> items =
                result.items().stream().map(OrderController::toResponse).toList();
        return Result.ok(
                new PageResult<>(
                        items, result.total(), result.page(), result.size(), result.nextCursor()));
    }

    /**
     * One of the buyer's own orders. A live order belonging to another buyer answers 404, not 403:
     * a 403 would confirm the id exists and turn this endpoint into an order-id oracle.
     */
    @Operation(summary = "Get one of my orders by id")
    @ApiResponse(responseCode = "404", description = "No such order for this buyer")
    @GetMapping("/{orderId}")
    public Result<OrderSummaryResponse> myOrder(@PathVariable UUID orderId) {
        StpUtil.checkLogin();
        UUID buyer = UUID.fromString(StpUtil.getLoginIdAsString());
        return Result.ok(
                orders.findMine(orderId, buyer)
                        .map(OrderController::toResponse)
                        .orElseThrow(() -> new BizException(OrderErrorCode.ORDER_NOT_FOUND)));
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
                view.unitPrice(),
                view.totalAmount(),
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
            String productImage,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            String currency,
            String status,
            Instant paidTime,
            Instant cancelTime,
            Instant createTime) {}
}
