package com.blocbuy.notification.web;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.common.web.Result;
import com.blocbuy.notification.service.NotificationAppService;
import io.swagger.v3.oas.annotations.Operation;
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

/** Recipient-scoped read API; event writes are internal application calls from #446. */
@Tag(name = "notifications", description = "My in-app notifications")
@SecurityRequirement(name = "blocbuyToken")
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationAppService notifications;

    /** Reads only the session recipient's notifications; pagination never selects an owner. */
    @Operation(summary = "List my notifications, newest first")
    @ApiResponse(responseCode = "200", description = "Bounded page in Result envelope")
    @ApiResponse(responseCode = "400", description = "Invalid pagination")
    @ApiResponse(responseCode = "401", description = "Login required")
    @GetMapping
    public Result<PageResult<NotificationResponse>> list(
            @RequestParam(required = false) @Min(1) Integer page,
            @RequestParam(required = false) @Min(1) @Max(PageQuery.MAX_SIZE) Integer size,
            HttpServletResponse response) {
        StpUtil.checkLogin();
        response.setHeader("Cache-Control", "no-store");
        var result =
                notifications.pageMine(
                        PageQuery.of(page, size), UUID.fromString(StpUtil.getLoginIdAsString()));
        var items =
                result.items().stream()
                        .map(item ->
                                new NotificationResponse(
                                        item.id(),
                                        item.type().code(),
                                        item.referenceId(),
                                        item.title(),
                                        item.content(),
                                        item.createTime()))
                        .toList();
        return Result.ok(
                new PageResult<>(
                        items, result.total(), result.page(), result.size(), result.nextCursor()));
    }

    /** No recipient IDs, transport IDs or audit actors are exposed to buyers. */
    public record NotificationResponse(
            UUID id,
            String type,
            UUID referenceId,
            String title,
            String content,
            Instant createTime) {}
}
