package com.blocbuy.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.blocbuy.catalog.model.ProductTeamTerms;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The two group-buy reads added to the product repository: team terms and the stock take. */
class ProductTeamTermsPersistenceTest {

    private final BizProductMapper mapper = mock(BizProductMapper.class);
    private final ProductRepositoryImpl repository = new ProductRepositoryImpl(mapper);
    private final UUID productId = UUID.randomUUID();

    private BizProductTeamTermsDO row(Integer targetSize, Integer lifetimeSeconds) {
        BizProductTeamTermsDO row = new BizProductTeamTermsDO();
        row.setProductId(productId);
        row.setTeamTargetSize(targetSize);
        row.setTeamLifetimeSeconds(lifetimeSeconds);
        return row;
    }

    @Test
    void configuredTermsComeBackAsAStartableValue() {
        when(mapper.selectTeamTerms(productId)).thenReturn(row(3, 7200));

        assertThat(repository.findTeamTerms(productId))
                .hasValueSatisfying(
                        terms -> {
                            assertThat(terms.productId()).isEqualTo(productId);
                            assertThat(terms.targetSize()).isEqualTo(3);
                            assertThat(terms.lifetimeSeconds()).isEqualTo(7200);
                            assertThat(terms.startable()).isTrue();
                        });
    }

    @Test
    void anUnconfiguredProductCarriesNullTermsRatherThanNoRow() {
        when(mapper.selectTeamTerms(productId)).thenReturn(row(null, null));

        assertThat(repository.findTeamTerms(productId))
                .hasValueSatisfying(terms -> assertThat(terms.startable()).isFalse());
    }

    @Test
    void aMissingProductHasNoTerms() {
        when(mapper.selectTeamTerms(productId)).thenReturn(null);

        assertThat(repository.findTeamTerms(productId)).isEmpty();
    }

    @Test
    void theStockTakeReportsWhetherTheConditionalUpdateMatched() {
        when(mapper.consumeOneUnit(productId)).thenReturn(1, 0);

        assertThat(repository.consumeOneUnit(productId)).isTrue();
        assertThat(repository.consumeOneUnit(productId)).isFalse();
    }

    @Test
    void termsAlwaysKnowTheirProduct() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new ProductTeamTerms(null, 3, 7200))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
