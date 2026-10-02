package com.blocbuy.catalog.web;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.catalog.model.ListingDetails;
import com.blocbuy.catalog.model.ListingReferences;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductStatus;
import com.blocbuy.catalog.model.ProductTeamTerms;
import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.service.AdminListingAppService;
import com.blocbuy.catalog.service.TeamPriceAppService;
import com.blocbuy.catalog.service.TeamTermsAppService;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.common.web.DecimalStringDeserializer;
import com.blocbuy.common.web.Result;
import com.blocbuy.identity.model.RoleCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import java.time.Instant;
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
        in = SecuritySchemeIn.COOKIE,
        paramName = "blocbuy-token")
@SecurityRequirement(name = "blocbuyToken")
@Tag(
        name = "admin-listings",
        description = "API-only listing management; admin role and catalog:product:write required")
@ApiResponse(responseCode = "200", description = "Success in the established Result envelope")
@ApiResponse(
        responseCode = "400",
        description =
                "Invalid input. Field-specific CATALOG_400_002 and CATALOG_400_001 errors use an array "
                        + "of {field, message}, matching COMMON_400_002 Bean Validation errors. "
                        + "field is the request JSON path: listing.<field> for listing content, productNo "
                        + "for the product number, or teamPrice.<field> for nested price policy input. "
                        + "message is safe to display; non-field domain failures may omit errors.")
@ApiResponse(responseCode = "401", description = "Login required")
@ApiResponse(
        responseCode = "403",
        description = "Administrator permission required or account disabled")
@ApiResponse(responseCode = "404", description = "Listing absent or deleted")
@ApiResponse(
        responseCode = "409",
        description =
                "Duplicate product number, illegal lifecycle action or retained business history")
@ApiResponse(
        responseCode = "412",
        description =
                "Version precondition failed: CATALOG_412_002 has errors: {currentVersion: <int>} "
                        + "with the stored listing version checked under the product row lock. "
                        + "For an optional teamPrice, CATALOG_412_001 has the same errors shape "
                        + "with the latest policy version (0 if none), checked under that lock. "
                        + "Reload before resubmitting; no mutation or automatic retry occurs.")
public class AdminListingController {
    /** True end-of-input in both Java and JSON Schema regexes, including a trailing newline. */
    private static final String INPUT_END = "(?![\\s\\S])";

    /** Require a nonzero digit while preserving exact decimal syntax and existing size limits. */
    private static final String POSITIVE_MONEY =
            "^(?=[\\d.]*[1-9])\\d{1,16}(\\.\\d{1,2})?" + INPUT_END;

    private final AdminListingAppService listings;
    private final TeamTermsAppService teamTerms;
    private final TeamPriceAppService prices;

    /** Trusted identity and lifecycle are never accepted from the creation body. */
    @PostMapping
    @Operation(
            operationId = "createAdminListing",
            summary = "Create a draft listing",
            description =
                    "Non-idempotent POST. Omit productNo or send null for a server-generated BB- number. "
                            + "Supplied productNo is retained and unique among non-deleted listings; a duplicate returns 409. "
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
            operationId = "getAdminListing",
            summary = "Read a managed listing",
            description =
                    "Includes drafts and off-sale listings, never deleted rows. latestPolicy is "
                            + "the latest non-deleted revision, even if expired or future, or null when none exists. "
                            + "Detail-only references counts all deal, quote and policy history, including deleted "
                            + "rows. deletable uses the DELETE lifecycle, sale, history and version-exhaustion rules "
                            + "at this snapshot; DELETE still rechecks under lock with expectedVersion.")
    public Result<AdminListingResponse> get(@PathVariable UUID productId) {
        return Result.ok(AdminListingResponse.from(listings.get(productId, administrator())));
    }

    /** Explicit bounded pagination, stable ordering and optional stored-status filter. */
    @GetMapping
    @Operation(
            operationId = "pageAdminListings",
            summary = "Page managed listings",
            description =
                    "Newest first, UUID tie-breaker. page starts at 1; size is 1-100. "
                            + "Omitted status includes every non-deleted state. latestPolicy is loaded "
                            + "in one batch for the page and is null when no non-deleted revision exists.")
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

    /** Historical revisions are protocol views, never an edit/delete surface. */
    @GetMapping("/{productId}/team-price-policy/revisions")
    @Operation(
            operationId = "pageAdminTeamPricePolicyRevisions",
            summary = "Page a listing's team-price policy revisions",
            description =
                    "Requires admin role, catalog:product:write and an active account. "
                            + "Non-deleted revisions only, including expired and future policies, "
                            + "ordered by version descending. Existing listing with no policy returns an empty page; "
                            + "missing/deleted listing returns 404. page starts at 1; size is 1-100 (default 20). "
                            + "Read-only snapshot; latest-policy authority and historical quotes are unchanged.")
    public Result<PageResult<TeamPriceController.PolicyResponse>> policyRevisions(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
        var result =
                prices.pagePolicyRevisions(productId, administrator(), new PageQuery(page, size));
        return Result.ok(
                new PageResult<>(
                        result.items().stream()
                                .map(TeamPriceController.PolicyResponse::from)
                                .toList(),
                        result.total(),
                        result.page(),
                        result.size(),
                        result.nextCursor()));
    }

    /** Dashboard totals use the same authorization as the paged administrator read. */
    @GetMapping("/counts")
    @Operation(
            summary = "Count managed listings and open group deals",
            description =
                    "One database snapshot. Listing totals count non-deleted rows by stored status, "
                            + "matching the admin page filters. openDeals spans all products and counts "
                            + "non-deleted deals with open status, currentSize below targetSize and "
                            + "expiry strictly after database time. withActivePolicy counts non-deleted "
                            + "listings whose latest non-deleted policy has validFrom <= database time "
                            + "and validUntil > database time, regardless of publication state.")
    public Result<ListingCountsResponse> counts() {
        var counts = listings.counts(administrator());
        return Result.ok(
                new ListingCountsResponse(
                        counts.draft(),
                        counts.onSale(),
                        counts.offSale(),
                        counts.openDeals(),
                        counts.withActivePolicy()));
    }

    /** Stable dashboard protocol view; every total is zero when its population is empty. */
    public record ListingCountsResponse(
            long draft,
            long onSale,
            long offSale,
            long openDeals,
            @Schema(
                            description =
                                    "Non-deleted listings whose latest non-deleted policy is valid now; "
                                            + "validFrom inclusive, validUntil exclusive, irrespective of listing status")
                    long withActivePolicy) {}

    /** A full content replacement leaves server-owned lifecycle and counters untouched. */
    @PutMapping("/{productId}")
    @Operation(
            operationId = "reviseAdminListing",
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
            operationId = "changeAdminListingStatus",
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

    /** Terms and the optional first-team price are one command, one transaction, one rule set. */
    @PutMapping("/{productId}/team-terms")
    @Operation(
            summary = "Set team terms and first-team price conditionally",
            description =
                    "Requires expectedVersion from the admin read; a stale value returns 412 without "
                            + "mutation. Replaces targetSize (>= 2) and lifetimeSeconds (2400-86400), allowed "
                            + "in every lifecycle state; a team already started keeps the terms it was "
                            + "started with. Optional teamPrice appends a policy revision in the same "
                            + "transaction and needs an on-sale listing; its expectedVersion is the latest "
                            + "policy version (0 for the first) and it must be below the original price. "
                            + "Errors: 400 COMMON_400_002 with a list of field errors for a missing or "
                            + "out-of-range expectedVersion, targetSize or lifetimeSeconds (Bean "
                            + "Validation); 400 CATALOG_400_001 with teamPrice.<field> field errors "
                            + "for a bad teamPrice (price not below the original price, validFrom not "
                            + "before validUntil, or validUntil already past); 409 CATALOG_409_001 when "
                            + "teamPrice is sent for a listing that is not on sale. Any rejection changes "
                            + "nothing. Not replay-idempotent: a replay carries a stale version. Each "
                            + "write is audited.")
    public Result<TeamTermsResponse> configureTeamTerms(
            @PathVariable UUID productId, @Valid @RequestBody TeamTermsRequest request) {
        UUID actor = administrator();
        return Result.ok(
                TeamTermsResponse.from(
                        teamTerms.configure(
                                productId,
                                new TeamTermsAppService.ConfigureTerms(
                                        request.expectedVersion(),
                                        request.targetSize(),
                                        request.lifetimeSeconds(),
                                        request.teamPrice() == null
                                                ? null
                                                : request.teamPrice().toPolicy()),
                                actor)));
    }

    /** Deletion keeps an audit row and never cascades to financial or business history. */
    @DeleteMapping("/{productId}")
    @Operation(
            operationId = "deleteAdminListing",
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
        StpUtil.checkRole(RoleCode.ADMIN);
        StpUtil.checkPermission("catalog:product:write");
        return UUID.fromString(StpUtil.getLoginIdAsString());
    }

    /** Client-writable content only; amount remains a strict decimal string on the wire. */
    @SuppressWarnings("java:S107") // flat protocol DTO mirroring the editable listing fields
    public record ListingContent(
            @NotBlank @Size(max = 256) String title,
            @Size(max = 256) String subtitle,
            @Size(max = 10000) String description,
            @Size(max = 1024) String coverImage,
            @Size(max = 20) List<@NotBlank @Size(max = 1024) String> images,
            @Size(max = 64) String category,
            @NotNull
                    @Pattern(regexp = POSITIVE_MONEY)
                    @JsonDeserialize(using = DecimalStringDeserializer.class)
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
     * Optional product number; omission generates it, and either form is immutable after create.
     */
    public record CreateListingRequest(
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}" + INPUT_END)
                    @Schema(
                            nullable = true,
                            description =
                                    "Optional immutable product number. Omitted or null: server-generated BB- number. "
                                            + "Supplied: retained, validated, and unique among non-deleted listings.")
                    String productNo,
            @NotNull @Valid ListingContent listing) {}

    /** Conditional full content replacement, not a stock-adjustment or pricing-policy command. */
    public record UpdateListingRequest(
            @NotNull @Valid ListingContent listing, @NotNull @Min(0) Integer expectedVersion) {}

    /**
     * Full replacement of the team terms with an optional first-team price; the bounds repeat the
     * domain's, so out-of-range terms are rejected here, before the domain's authoritative check.
     */
    public record TeamTermsRequest(
            @NotNull @Min(0) Integer expectedVersion,
            @NotNull @Min(ProductTeamTerms.MIN_TARGET_SIZE) Integer targetSize,
            @NotNull
                    @Min(ProductTeamTerms.MIN_LIFETIME_SECONDS)
                    @Max(ProductTeamTerms.MAX_LIFETIME_SECONDS)
                    Integer lifetimeSeconds,
            @Valid TeamPriceRequest teamPrice) {}

    /** Policy revision to append with the terms; same shape as the standalone policy endpoint. */
    public record TeamPriceRequest(
            @NotNull
                    @Pattern(regexp = POSITIVE_MONEY)
                    @JsonDeserialize(using = DecimalStringDeserializer.class)
                    @Schema(
                            example = "89.00",
                            description =
                                    "SGD major units as a JSON string, strictly below the original price")
                    String amount,
            @NotNull @Pattern(regexp = "^sgd" + INPUT_END) String currency,
            @NotNull Instant validFrom,
            @NotNull Instant validUntil,
            @NotNull @Min(0) Integer expectedVersion) {
        /** Convert only after Bean Validation; the policy's own rules run in the use case. */
        TeamPriceAppService.ConfigurePolicy toPolicy() {
            return new TeamPriceAppService.ConfigurePolicy(
                    new BigDecimal(amount), currency, validFrom, validUntil, expectedVersion);
        }
    }

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
            int version,
            TeamTermsView teamTerms,
            @JsonInclude(JsonInclude.Include.ALWAYS)
                    @Schema(
                            nullable = true,
                            description =
                                    "Latest non-deleted revision, even if expired or future; null when none exists")
                    LatestPolicyResponse latestPolicy,
            @JsonInclude(JsonInclude.Include.NON_NULL)
                    @Schema(
                            description =
                                    "Present on the single admin GET only; all retained history, including deleted rows")
                    ListingReferencesResponse references,
            @JsonInclude(JsonInclude.Include.NON_NULL)
                    @Schema(
                            description =
                                    "Present on the single admin GET only; snapshot eligibility, not a deletion reservation")
                    Boolean deletable) {
        /** Convert pure business values only; no persistence object crosses the HTTP boundary. */
        static AdminListingResponse from(AdminListingAppService.ManagedListing listing) {
            Product product = listing.product();
            return new AdminListingResponse(
                    product.id(),
                    product.productNo(),
                    ListingContent.from(product),
                    product.status().code(),
                    product.pricingMode().code(),
                    product.soldCount(),
                    product.version(),
                    TeamTermsView.from(product.teamTerms()),
                    LatestPolicyResponse.from(listing.latestPolicy()),
                    ListingReferencesResponse.from(listing.references()),
                    listing.deletable());
        }
    }

    /** Editor-only history projection; no cross-domain aggregate crosses the HTTP boundary. */
    public record ListingReferencesResponse(long deals, long quotes, long policies) {
        static ListingReferencesResponse from(ListingReferences references) {
            return references == null
                    ? null
                    : new ListingReferencesResponse(
                            references.deals(), references.quotes(), references.policies());
        }
    }

    /** Policy summary uses the existing policy read's exact decimal-string representation. */
    public record LatestPolicyResponse(
            String amount, String currency, Instant validFrom, Instant validUntil, int version) {
        static LatestPolicyResponse from(TeamPricePolicy policy) {
            return policy == null
                    ? null
                    : new LatestPolicyResponse(
                            policy.amount().toPlainString(),
                            policy.currency(),
                            policy.validFrom(),
                            policy.validUntil(),
                            policy.version());
        }
    }

    /**
     * Stored team terms; members are null and configured is false for a never-configured listing.
     */
    public record TeamTermsView(
            @Schema(nullable = true) Integer targetSize,
            @Schema(nullable = true) Integer lifetimeSeconds,
            @Schema(
                            description =
                                    "Both terms are set and inside the bounds. START can still fail:"
                                            + " it also needs an on-sale listing with stock and a"
                                            + " team price policy in force for at least the next"
                                            + " 2400 seconds")
                    boolean configured) {
        static TeamTermsView from(ProductTeamTerms terms) {
            return new TeamTermsView(
                    terms.targetSize(), terms.lifetimeSeconds(), terms.configured());
        }
    }

    /**
     * New product version to send next, the terms now in force and the policy appended, if any. An
     * absent policy is omitted, not null: the generator cannot express a nullable {@code $ref}.
     */
    public record TeamTermsResponse(
            UUID productId,
            int version,
            TeamTermsView teamTerms,
            @Schema(description = "Omitted when the command set no team price")
                    @JsonInclude(JsonInclude.Include.NON_NULL)
                    TeamPriceController.PolicyResponse teamPrice) {
        static TeamTermsResponse from(TeamTermsAppService.TeamTermsResult result) {
            return new TeamTermsResponse(
                    result.product().id(),
                    result.product().version(),
                    TeamTermsView.from(result.product().teamTerms()),
                    result.teamPrice() == null
                            ? null
                            : TeamPriceController.PolicyResponse.from(result.teamPrice()));
        }
    }
}
