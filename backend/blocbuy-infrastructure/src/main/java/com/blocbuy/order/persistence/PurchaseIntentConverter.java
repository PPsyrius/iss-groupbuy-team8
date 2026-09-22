package com.blocbuy.order.persistence;

import com.blocbuy.order.model.PurchaseIntent;
import com.blocbuy.order.model.PurchaseOperation;

final class PurchaseIntentConverter {

    private PurchaseIntentConverter() {}

    static PurchaseIntent toDomain(BizPurchaseIntentDO row) {
        if (row == null) {
            return null;
        }
        return PurchaseIntent.reconstitute(
                row.getId(),
                new PurchaseIntent.Terms(
                        row.getUserId(),
                        PurchaseOperation.fromCode(row.getOperation()),
                        row.getDealId(),
                        row.getProductId(),
                        row.getProductTitle(),
                        row.getProductImage(),
                        row.getAmount(),
                        row.getCurrency(),
                        row.getTargetSize() == null ? 0 : row.getTargetSize()),
                row.getQuoteValidUntil(),
                row.getBusinessDeadline(),
                row.getFulfilledPaymentId(),
                row.getCreateTime(),
                row.getVersion() == null ? 0 : row.getVersion());
    }

    static BizPurchaseIntentDO toRow(PurchaseIntent intent) {
        BizPurchaseIntentDO row = new BizPurchaseIntentDO();
        row.setId(intent.id());
        row.setUserId(intent.userId());
        row.setOperation(intent.operation().code());
        row.setDealId(intent.dealId());
        row.setProductId(intent.productId());
        row.setProductTitle(intent.productTitle());
        row.setProductImage(intent.productImage());
        row.setAmount(intent.amount());
        row.setCurrency(intent.currency());
        row.setTargetSize(intent.targetSize());
        row.setQuoteValidUntil(intent.quoteValidUntil());
        row.setBusinessDeadline(intent.businessDeadline());
        row.setFulfilledPaymentId(intent.fulfilledPaymentId());
        row.setCreateTime(intent.createTime());
        row.setVersion(intent.version());
        return row;
    }
}
