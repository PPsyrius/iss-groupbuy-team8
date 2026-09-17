package com.blocbuy.catalog.web;

import com.blocbuy.catalog.query.ListingDetailView;
import com.blocbuy.catalog.query.ListingQuery;
import com.blocbuy.catalog.query.ListingView;
import com.blocbuy.catalog.service.ProductAppService;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.common.web.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
 * Product resource controller. Protocol adaptation only: parse/validate requests, convert to
 * commands/queries, call the application service. No business transactions, no mapper calls, no
 * direct cache manipulation.
 *
 * <p>Browse endpoints are anonymous (decision: buyers may browse the catalog without logging in);
 * the Sa-Token interceptor exempts GET on this path only. Each listing carries its resolved dual
 * price — individual vs group-buy — so the UI shows what joining the team saves.
 */
@Tag(name = "products", description = "Product catalog browse")
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductAppService productAppService;

    /** Paginated on-sale listings; optional keyword search plus category filter. Anonymous. */
    @Operation(summary = "Browse or search on-sale listings with dual pricing")
    @GetMapping
    public Result<PageResult<ProductSummaryResponse>> pageListings(
            @RequestParam(required = false) @Min(1) Integer page,
            @RequestParam(required = false) @Min(1) @Max(PageQuery.MAX_SIZE) Integer size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false)
                    @Size(max = ListingQuery.MAX_KEYWORD_LENGTH)
                    @Parameter(
                            description =
                                    "Up to 64 characters. Whitespace-separated terms all match "
                                            + "across title, subtitle, or description.")
                    String keyword,
            @RequestParam(defaultValue = "false")
                    @Parameter(
                            description =
                                    "Best-effort Jev semantic re-ranking within the returned page when "
                                            + "keyword is nonblank; otherwise ignored. Falls back to catalog "
                                            + "order when unavailable.")
                    boolean ai) {
        if (keyword == null || keyword.isBlank()) {
            PageResult<ListingView> result = productAppService.pageListings(page, size, category);
            return toPageResult(result);
        }
        PageResult<ListingView> result =
                productAppService.search(ListingQuery.of(keyword, category, page, size, ai));
        return toPageResult(result);
    }

    /** One listing with dual price and active deal progress. Anonymous. */
    @Operation(summary = "Listing detail with dual price and deal progress")
    @GetMapping("/{id}")
    public Result<ProductDetailResponse> getListing(@PathVariable UUID id) {
        return Result.ok(toDetail(productAppService.getListingDetail(id)));
    }

    /** Converts an application page into the public response envelope. */
    private static Result<PageResult<ProductSummaryResponse>> toPageResult(
            PageResult<ListingView> result) {
        List<ProductSummaryResponse> items =
                result.items().stream().map(ProductController::toSummary).toList();
        return Result.ok(
                new PageResult<>(
                        items, result.total(), result.page(), result.size(), result.nextCursor()));
    }

    private static ProductSummaryResponse toSummary(ListingView view) {
        return new ProductSummaryResponse(
                view.id(),
                view.title(),
                view.subtitle(),
                view.coverImage(),
                view.category(),
                view.individualPrice(),
                view.groupPrice(),
                view.currency());
    }

    private static ProductDetailResponse toDetail(ListingDetailView view) {
        return new ProductDetailResponse(
                view.id(),
                view.title(),
                view.subtitle(),
                view.description(),
                view.coverImage(),
                view.images(),
                view.category(),
                view.individualPrice(),
                view.groupPrice(),
                view.currency(),
                view.stock(),
                view.soldCount(),
                view.deal() == null
                        ? null
                        : new ActiveDealResponse(
                                view.deal().dealId(),
                                view.deal().targetSize(),
                                view.deal().currentSize(),
                                view.deal().expireTime()));
    }

    /** Listing summary for the browse grid; {@code groupPrice} null when no open deal. */
    public record ProductSummaryResponse(
            UUID id,
            String title,
            String subtitle,
            String coverImage,
            String category,
            BigDecimal individualPrice,
            BigDecimal groupPrice,
            String currency) {}

    /** Full listing for the detail page; {@code deal} null when no open deal. */
    public record ProductDetailResponse(
            UUID id,
            String title,
            String subtitle,
            String description,
            String coverImage,
            List<String> images,
            String category,
            BigDecimal individualPrice,
            BigDecimal groupPrice,
            String currency,
            int stock,
            int soldCount,
            ActiveDealResponse deal) {

        /** Defensive copy: response records never alias a caller's mutable list. */
        public ProductDetailResponse {
            images = images == null ? List.of() : List.copyOf(images);
        }
    }

    /** Active deal progress: paid members so far vs target, and when the deal closes. */
    public record ActiveDealResponse(
            UUID dealId, int targetSize, int currentSize, Instant expireTime) {}
}
