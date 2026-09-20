package com.blocbuy.order.web;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.common.web.Result;
import com.blocbuy.order.repository.OrderHistory;
import com.blocbuy.order.service.OrderHistoryService;
import com.blocbuy.payment.service.StripeFeatureSwitch;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * direct cache manipulation. Buyer identity always comes from the authenticated session.
 */
@Tag(name = "orders", description = "Order resource")
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "blocbuyToken")
public class StripeOrderHistoryController {
    private final OrderHistoryService history;
    private final StripeFeatureSwitch feature;

    @GetMapping
    public Result<PageResult<OrderHistory.Entry>> pageOrders(
            @RequestParam(defaultValue = "1") @Min(1) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        feature.requireEnabled();
        return Result.ok(history.page(UUID.fromString(StpUtil.getLoginIdAsString()), page, size));
    }
}
