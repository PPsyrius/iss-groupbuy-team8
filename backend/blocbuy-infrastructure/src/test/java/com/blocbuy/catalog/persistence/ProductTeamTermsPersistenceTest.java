package com.blocbuy.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.PricingMode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductStatus;
import com.blocbuy.catalog.model.ProductTeamTerms;
import com.blocbuy.catalog.model.TeamTermsChange;
import com.blocbuy.common.exception.BizException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Locked-product terms and the conditional stock take. */
class ProductTeamTermsPersistenceTest {

    private final BizProductMapper mapper = mock(BizProductMapper.class);
    private final ProductRepositoryImpl repository = new ProductRepositoryImpl(mapper);
    private final UUID productId = UUID.randomUUID();

    private BizProductDO row(Integer targetSize, Integer lifetimeSeconds) {
        BizProductDO row = ProductConverter.toRow(configuredProduct());
        row.setTeamTargetSize(targetSize);
        row.setTeamLifetimeSeconds(lifetimeSeconds);
        return row;
    }

    @Test
    void configuredTermsComeBackAsAStartableValue() {
        when(mapper.selectActiveByIdForUpdate(productId)).thenReturn(row(3, 7200));

        assertThat(repository.lockById(productId))
                .hasValueSatisfying(
                        product -> {
                            ProductTeamTerms terms = product.teamTerms();
                            assertThat(terms.productId()).isEqualTo(productId);
                            assertThat(terms.targetSize()).isEqualTo(3);
                            assertThat(terms.lifetimeSeconds()).isEqualTo(7200);
                            assertThat(terms.configured()).isTrue();
                        });
    }

    @Test
    void anUnconfiguredProductCarriesNullTermsRatherThanNoRow() {
        when(mapper.selectActiveByIdForUpdate(productId)).thenReturn(row(null, null));

        assertThat(repository.lockById(productId))
                .hasValueSatisfying(
                        product -> assertThat(product.teamTerms().configured()).isFalse());
    }

    @Test
    void aMissingProductCannotBeLocked() {
        when(mapper.selectActiveByIdForUpdate(productId)).thenReturn(null);

        assertThat(repository.lockById(productId)).isEmpty();
    }

    @Test
    void theStockTakeReportsWhetherTheConditionalUpdateMatched() {
        when(mapper.consumeOneUnit(productId)).thenReturn(1, 0);

        assertThat(repository.consumeOneUnit(productId)).isTrue();
        assertThat(repository.consumeOneUnit(productId)).isFalse();
    }

    @Test
    void termsWriteIsConditionalOnTheExpectedVersion() {
        UUID actor = UUID.randomUUID();
        Product revised = configuredProduct();
        when(mapper.updateTeamTerms(any(), eq(2), eq(actor))).thenReturn(1, 0);

        repository.updateTeamTerms(revised, 2, actor);

        verify(mapper)
                .updateTeamTerms(
                        argThat(
                                row ->
                                        productId.equals(row.getId())
                                                && Integer.valueOf(3)
                                                        .equals(row.getTeamTargetSize())
                                                && Integer.valueOf(7200)
                                                        .equals(row.getTeamLifetimeSeconds())),
                        eq(2),
                        eq(actor));
        assertThatThrownBy(() -> repository.updateTeamTerms(revised, 2, actor))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.PRODUCT_VERSION_CONFLICT));
    }

    @Test
    void theAuditRowMustBeStored() {
        TeamTermsChange change =
                new TeamTermsChange(
                        productId,
                        3,
                        ProductTeamTerms.unset(productId),
                        new ProductTeamTerms(productId, 3, 7200),
                        null,
                        UUID.randomUUID());
        when(mapper.insertTeamTermsLog(any())).thenReturn(1, 0);

        repository.appendTeamTermsLog(change);

        verify(mapper)
                .insertTeamTermsLog(
                        argThat(
                                row ->
                                        productId.equals(row.getProductId())
                                                && Integer.valueOf(3).equals(row.getNewTargetSize())
                                                && row.getOldTargetSize() == null));
        assertThatThrownBy(() -> repository.appendTeamTermsLog(change))
                .isInstanceOf(IllegalStateException.class);
    }

    private Product configuredProduct() {
        return Product.reconstitute(
                productId,
                "SKU-T",
                "Tea",
                null,
                null,
                null,
                List.of(),
                null,
                new BigDecimal("10.00"),
                "sgd",
                5,
                0,
                ProductStatus.DRAFT,
                PricingMode.DUAL,
                3,
                new ProductTeamTerms(productId, 3, 7200));
    }

    @Test
    void termsAlwaysKnowTheirProduct() {
        assertThatThrownBy(() -> new ProductTeamTerms(null, 3, 7200))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
