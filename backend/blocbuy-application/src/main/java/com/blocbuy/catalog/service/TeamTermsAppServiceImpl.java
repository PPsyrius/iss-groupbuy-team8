package com.blocbuy.catalog.service;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.TeamTermsChange;
import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.repository.ProductRepository;
import com.blocbuy.catalog.service.TeamPriceAppService.ConfigurePolicy;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.service.UserAppService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One bounded local transaction that takes only the product row lock, first, exactly like a START
 * quote, a quotation and a listing edit: a START sees the old terms or the new ones, never a mix,
 * and no second lock is added to the global order. No remote call and no retry loop run inside it.
 */
@Service
@RequiredArgsConstructor
public class TeamTermsAppServiceImpl implements TeamTermsAppService {
    private final ProductRepository products;
    private final TeamPriceAppService prices;
    private final UserAppService users;

    @Override
    @Transactional(timeout = 10)
    public TeamTermsResult configure(UUID productId, ConfigureTerms command, UUID actorId) {
        Product current =
                products.lockById(productId)
                        .orElseThrow(() -> new BizException(CatalogErrorCode.PRODUCT_NOT_FOUND));
        users.requireActive(actorId);
        // Version before rules: a stale client is told to re-read, not that its data is invalid.
        current.requireVersion(command.expectedVersion());

        ConfigurePolicy price = command.teamPrice();
        Product revised =
                current.configureTeamTerms(command.targetSize(), command.lifetimeSeconds());
        products.updateTeamTerms(revised, command.expectedVersion(), actorId);

        // Joins this transaction and owns every price rule; a rejected policy rolls the terms back.
        TeamPricePolicy policy = price == null ? null : prices.configure(productId, actorId, price);
        products.appendTeamTermsLog(
                new TeamTermsChange(
                        productId,
                        revised.version(),
                        current.teamTerms(),
                        revised.teamTerms(),
                        policy == null ? null : policy.version(),
                        actorId));
        return new TeamTermsResult(revised, policy);
    }
}
