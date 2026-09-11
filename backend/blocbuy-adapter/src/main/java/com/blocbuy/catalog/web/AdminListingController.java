package com.blocbuy.catalog.web;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.catalog.model.ListingDetails;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductStatus;
import com.blocbuy.catalog.service.AdminListingAppService;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.common.web.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * UC9 protocol adapter; existing public product browse and quotation contracts remain unchanged.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.API_V1 + "/admin/products")
@SecurityScheme(
        name = "blocbuyToken",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "blocbuy-token")
@SecurityRequirement(name = "blocbuyToken")
@Tag(
        name = "admin-listings",
        description = "API-only listing management; admin role and catalog:product:write required")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Success in the established Result envelope"),
    @ApiResponse(responseCode = "400", description = "Invalid input"),
    @ApiResponse(responseCode = "401", description = "Login required"),
    @ApiResponse(
            responseCode = "403",
            description = "Administrator permission required or account disabled"),
    @ApiResponse(responseCode = "404", description = "Listing absent or deleted"),
    @ApiResponse(
            responseCode = "409",
            description =
                    "Duplicate product number, illegal lifecycle action or retained business history"),
    @ApiResponse(responseCode = "412", description = "Stale listing version precondition")
})
public class AdminListingController {
    /** True end-of-input in both Java and JSON Schema regexes, including a trailing newline. */
    private static final String INPUT_END = "(?![\\s\\S])";

    /** Require a nonzero digit while preserving exact decimal syntax and existing size limits. */
    private static final String POSITIVE_MONEY =
            "^(?=[0-9.]*[1-9])[0-9]{1,16}(\\.[0-9]{1,2})?" + INPUT_END;

    private final AdminListingAppService listings;

    /** Trusted identity and lifecycle are never accepted from the creation body. */
    @PostMapping
    @Operation(
            summary = "Create a draft listing",
            description =
                    "Non-idempotent POST. productNo is unique among non-deleted listings; a duplicate returns 409. "
                            + "Server assigns UUID, draft status, dual pricing mode and version 0. SGD only.")
    public Result<AdminListingResponse> create(@Valid @RequestBody CreateListingRequest request) {
        UUID actor = administrator();
        return Result.ok(
                AdminListingResponse.from(
                        listings.create(request.productNo(), request.listing().toDomain(), actor)));
    }

    /** Separate admin route avoids changing the anonymously accessible buyer GET contract. */
    @GetMapping("/{productId}")
    @Operation(
            summary = "Read a managed listing",
            description = "Includes drafts and off-sale listings, never deleted rows.")
    public Result<AdminListingResponse> get(@PathVariable UUID productId) {
        return Result.ok(AdminListingResponse.from(listings.get(productId, administrator())));
    }

    /** Explicit bounded pagination, stable ordering and optional stored-status filter. */
    @GetMapping
    @Operation(
            summary = "Page managed listings",
            description =
                    "Newest first, UUID tie-breaker. page starts at 1; size is 1-100. "
                            + "Omitted status includes every non-deleted state.")
    public Result<PageResult<AdminListingResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size,
            @RequestParam(required = false)
                    @Pattern(regexp = "^(?:draft|on_sale|off_sale)" + INPUT_END)
                    String status) {
        var result =
                listings.page(
                        new PageQuery(page, size),
                        status == null ? null : ProductStatus.fromCode(status),
                        administrator());
        return Result.ok(
                new PageResult<>(
                        result.items().stream().map(AdminListingResponse::from).toList(),
                        result.total(),
                        result.page(),
                        result.size(),
                        result.nextCursor()));
    }

    /** A full content replacement leaves server-owned lifecycle and counters untouched. */
    @PutMapping("/{productId}")
    @Operation(
            summary = "Replace listing content conditionally",
            description =
                    "Requires expectedVersion from the admin read. Stale retries return 412 without another mutation. "
                            + "productNo, pricingMode and soldCount are immutable here. After first publication "
                            + "originalPrice/currency/stock must remain unchanged; inventory adjustment is not this API.")
    public Result<AdminListingResponse> revise(
            @PathVariable UUID productId, @Valid @RequestBody UpdateListingRequest request) {
        UUID actor = administrator();
        return Result.ok(
                AdminListingResponse.from(
                        listings.revise(
                                productId,
                                request.listing().toDomain(),
                                request.expectedVersion(),
                                actor)));
    }

    /** Lifecycle intent is resolved by the aggregate's State behavior. */
    @PutMapping("/{productId}/status")
    @Operation(
            summary = "Publish or delist conditionally",
            description =
                    "draft -> on_sale <-> off_sale; returning to draft is forbidden. "
                            + "Publication requires positive stock and price. "
                            + "Every successful mutation increments the product version. "
                            + "Existing quote/deal snapshots remain unchanged.")
    public Result<AdminListingResponse> changeStatus(
            @PathVariable UUID productId, @Valid @RequestBody ListingStatusRequest request) {
        return Result.ok(
                AdminListingResponse.from(
                        listings.changeStatus(
                                productId,
                                ProductStatus.fromCode(request.status()),
                                request.expectedVersion(),
                                administrator())));
    }

    /** Deletion keeps an audit row and never cascades to financial or business history. */
    @DeleteMapping("/{productId}")
    @Operation(
            summary = "Soft-delete an unreferenced draft",
            description =
                    "Only never-published drafts with no business references may be deleted. "
                            + "Published/off-sale products must be delisted instead. Requires expectedVersion. "
                            + "Repeating deletion returns 404 and makes no further change; no physical purge.")
    public Result<Void> delete(
            @PathVariable UUID productId, @RequestParam @Min(0) int expectedVersion) {
        listings.delete(productId, expectedVersion, administrator());
        return Result.ok();
    }

    /** All admin reads and writes check both RBAC role and capability; actor is session-derived. */
    private static UUID administrator() {
        StpUtil.checkLogin();
        StpUtil.checkRole("admin");
        StpUtil.checkPermission("catalog:product:write");
        return UUID.fromString(StpUtil.getLoginIdAsString());
    }

    /** Client-writable content only; amount remains a strict decimal string on the wire. */
    public record ListingContent(
            @NotBlank @Size(max = 256) String title,
            @Size(max = 256) String subtitle,
            @Size(max = 10000) String description,
            @Size(max = 1024) String coverImage,
            @Size(max = 20) List<@NotBlank @Size(max = 1024) String> images,
            @Size(max = 64) String category,
            @NotNull
                    @Pattern(regexp = POSITIVE_MONEY)
                    @JsonDeserialize(using = TeamPriceController.DecimalStringDeserializer.class)
                    @Schema(
                            example = "12.00",
                            description = "Positive exact SGD major units; JSON string, not number")
                    String originalPrice,
            @NotNull @Pattern(regexp = "^sgd" + INPUT_END) String currency,
            @NotNull @Min(0) Integer stock) {
        /**
         * Convert only after Bean Validation; domain validation is authoritative for invariants.
         */
        ListingDetails toDomain() {
            return new ListingDetails(
                    title,
                    subtitle,
                    description,
                    coverImage,
                    images,
                    category,
                    new BigDecimal(originalPrice),
                    currency,
                    stock);
        }

        /** Hide aggregate representation behind a stable protocol DTO. */
        static ListingContent from(Product product) {
            return new ListingContent(
                    product.title(),
                    product.subtitle(),
                    product.description(),
                    product.coverImage(),
                    product.images(),
                    product.category(),
                    product.originalPrice().toPlainString(),
                    product.currency(),
                    product.stock());
        }
    }

    /**
     * Stable product number is supplied once; all other identity and lifecycle fields are
     * server-owned.
     */
    public record CreateListingRequest(
            @NotNull @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}" + INPUT_END)
                    String productNo,
            @NotNull @Valid ListingContent listing) {}

    /** Conditional full content replacement, not a stock-adjustment or pricing-policy command. */
    public record UpdateListingRequest(
            @NotNull @Valid ListingContent listing, @NotNull @Min(0) Integer expectedVersion) {}

    /** Public lifecycle targets deliberately exclude draft. */
    public record ListingStatusRequest(
            @NotNull @Pattern(regexp = "^(?:on_sale|off_sale)" + INPUT_END) String status,
            @NotNull @Min(0) Integer expectedVersion) {}

    /** Versioned administrator view; this does not alter the buyer listing response. */
    public record AdminListingResponse(
            UUID id,
            String productNo,
            ListingContent listing,
            String status,
            String pricingMode,
            int soldCount,
            int version) {
        /** Convert pure business values only; no persistence object crosses the HTTP boundary. */
        static AdminListingResponse from(Product product) {
            return new AdminListingResponse(
                    product.id(),
                    product.productNo(),
                    ListingContent.from(product),
                    product.status().code(),
                    product.pricingMode().code(),
                    product.soldCount(),
                    product.version());
        }
    }
}
