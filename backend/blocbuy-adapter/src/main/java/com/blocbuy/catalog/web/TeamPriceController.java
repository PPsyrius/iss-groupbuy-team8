package com.blocbuy.catalog.web;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.pricing.TeamPriceQuote;
import com.blocbuy.catalog.service.TeamPriceAppService;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.web.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Authenticated catalog quotation; no buyer-supplied owner or price on quote creation. */
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1)
@Tag(
        name = "team-prices",
        description = "First-team price policies and immutable quotations; not checkout")
@RequiredArgsConstructor
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Success in Result envelope"),
    @ApiResponse(responseCode = "400", description = "Invalid request or policy"),
    @ApiResponse(responseCode = "401", description = "Login required"),
    @ApiResponse(responseCode = "403", description = "Permission denied or account disabled"),
    @ApiResponse(responseCode = "404", description = "Resource not found"),
    @ApiResponse(responseCode = "409", description = "Policy unavailable"),
    @ApiResponse(responseCode = "412", description = "Policy version precondition failed")
})
public class TeamPriceController {
    private static final String WRITE_PERMISSION = "catalog:product:write";
    private final TeamPriceAppService prices;

    @PutMapping("/products/{productId}/team-price-policy")
    @Operation(
            summary = "Configure first-team pricing",
            description =
                    "Requires catalog:product:write. "
                            + "expectedVersion 0 creates the first revision; later writes must match the latest version. "
                            + "Stale retries return 412 without another revision. Latest policy always replaces older policy.")
    public Result<PolicyResponse> configure(
            @PathVariable UUID productId, @Valid @RequestBody PolicyRequest request) {
        StpUtil.checkPermission(WRITE_PERMISSION);
        TeamPricePolicy policy =
                prices.configure(
                        productId,
                        currentUser(),
                        new TeamPriceAppService.ConfigurePolicy(
                                new BigDecimal(request.amount()),
                                request.currency(),
                                request.validFrom(),
                                request.validUntil(),
                                request.expectedVersion()));
        return Result.ok(PolicyResponse.from(policy));
    }

    @GetMapping("/products/{productId}/team-price-policy")
    @Operation(
            summary = "Read latest first-team price policy",
            description =
                    "Requires catalog:product:write, including on this otherwise-public product path.")
    public Result<PolicyResponse> getPolicy(@PathVariable UUID productId) {
        StpUtil.checkLogin();
        StpUtil.checkPermission(WRITE_PERMISSION);
        return Result.ok(PolicyResponse.from(prices.getPolicy(productId, currentUser())));
    }

    @PostMapping("/team-price-quotes")
    @Operation(
            summary = "Issue a first-team quote",
            description =
                    "Authenticated, non-idempotent snapshot creation: "
                            + "each call creates a distinct quote. Server determines SGD amount. Valid for at most 15 minutes, "
                            + "clipped to policy expiry. Does not reserve stock, start a team, or create a payment.")
    public Result<QuoteResponse> issue(@Valid @RequestBody QuoteRequest request) {
        return Result.ok(QuoteResponse.from(prices.issueQuote(request.productId(), currentUser())));
    }

    @GetMapping("/team-price-quotes/{quoteId}")
    @Operation(
            summary = "Read your quote snapshot",
            description =
                    "Owner-scoped historical read, including expired quotes. "
                            + "Other owners receive the same 404 as an unknown quote. This is not checkout eligibility validation.")
    public Result<QuoteResponse> getQuote(@PathVariable UUID quoteId) {
        return Result.ok(QuoteResponse.from(prices.getQuote(quoteId, currentUser())));
    }

    private static UUID currentUser() {
        return UUID.fromString(StpUtil.getLoginIdAsString());
    }

    public record PolicyRequest(
            @NotNull
                    @Pattern(regexp = "[0-9]{1,16}(\\.[0-9]{1,2})?")
                    @JsonDeserialize(using = DecimalStringDeserializer.class)
                    @Schema(
                            example = "89.00",
                            description =
                                    "Decimal string in SGD major units, strictly below product original price")
                    String amount,
            @NotNull @Pattern(regexp = "sgd") String currency,
            @NotNull Instant validFrom,
            @NotNull Instant validUntil,
            @NotNull @Min(0) Integer expectedVersion) {}

    public record QuoteRequest(@NotNull UUID productId) {}

    /** Prevent Jackson's default numeric coercion from weakening the decimal-string contract. */
    public static final class DecimalStringDeserializer extends ValueDeserializer<String> {
        @Override
        public String deserialize(JsonParser parser, DeserializationContext context)
                throws JacksonException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return (String) context.handleUnexpectedToken(String.class, parser);
            }
            return parser.getText();
        }
    }

    public record PolicyResponse(
            UUID productId,
            String amount,
            String currency,
            Instant validFrom,
            Instant validUntil,
            int version) {
        static PolicyResponse from(TeamPricePolicy policy) {
            return new PolicyResponse(
                    policy.productId(),
                    policy.amount().toPlainString(),
                    policy.currency(),
                    policy.validFrom(),
                    policy.validUntil(),
                    policy.version());
        }
    }

    public record QuoteResponse(
            UUID id,
            UUID productId,
            int policyVersion,
            String amount,
            String currency,
            Instant issuedAt,
            Instant expiresAt) {
        static QuoteResponse from(TeamPriceQuote quote) {
            return new QuoteResponse(
                    quote.id(),
                    quote.productId(),
                    quote.policyVersion(),
                    quote.amount().toPlainString(),
                    quote.currency(),
                    quote.issuedAt(),
                    quote.expiresAt());
        }
    }
}
