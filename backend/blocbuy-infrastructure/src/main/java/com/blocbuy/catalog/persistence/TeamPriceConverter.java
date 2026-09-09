package com.blocbuy.catalog.persistence;

import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.pricing.TeamPriceQuote;

/** Explicit storage/domain boundary, including immutable snapshot reconstitution. */
final class TeamPriceConverter {
    private TeamPriceConverter() {}

    static TeamPricePolicy toDomain(TeamPricePolicyDO row) {
        return row == null
                ? null
                : new TeamPricePolicy(
                        row.getProductId(),
                        row.getAmount(),
                        row.getCurrency(),
                        row.getValidFrom(),
                        row.getValidUntil(),
                        row.getVersion());
    }

    static TeamPricePolicyDO toRow(TeamPricePolicy policy) {
        var row = new TeamPricePolicyDO();
        row.setProductId(policy.productId());
        row.setAmount(policy.amount());
        row.setCurrency(policy.currency());
        row.setValidFrom(policy.validFrom());
        row.setValidUntil(policy.validUntil());
        row.setVersion(policy.version());
        return row;
    }

    static TeamPriceQuote toDomain(TeamPriceQuoteDO row) {
        return row == null
                ? null
                : new TeamPriceQuote(
                        row.getId(),
                        row.getProductId(),
                        row.getOwnerId(),
                        row.getPolicyVersion(),
                        row.getAmount(),
                        row.getCurrency(),
                        row.getIssuedAt(),
                        row.getExpiresAt());
    }

    static TeamPriceQuoteDO toRow(TeamPriceQuote quote) {
        var row = new TeamPriceQuoteDO();
        row.setId(quote.id());
        row.setProductId(quote.productId());
        row.setOwnerId(quote.ownerId());
        row.setPolicyVersion(quote.policyVersion());
        row.setAmount(quote.amount());
        row.setCurrency(quote.currency());
        row.setIssuedAt(quote.issuedAt());
        row.setExpiresAt(quote.expiresAt());
        return row;
    }
}
