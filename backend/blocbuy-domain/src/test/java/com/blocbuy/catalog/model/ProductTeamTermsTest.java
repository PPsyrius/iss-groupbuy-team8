package com.blocbuy.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Terms value semantics and how the aggregate carries them. */
class ProductTeamTermsTest {
    private static final UUID ID = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    @Test
    void unsetTermsAreNeverConfigured() {
        ProductTeamTerms unset = ProductTeamTerms.unset(ID);
        assertThat(unset.productId()).isEqualTo(ID);
        assertThat(unset.targetSize()).isNull();
        assertThat(unset.lifetimeSeconds()).isNull();
        assertThat(unset.configured()).isFalse();
    }

    @Test
    void configuredNeedsBothTermsInsideTheBounds() {
        assertThat(new ProductTeamTerms(ID, 2, 2400).configured()).isTrue();
        assertThat(new ProductTeamTerms(ID, 2, 86_400).configured()).isTrue();
        assertThat(new ProductTeamTerms(ID, 1, 2400).configured()).isFalse();
        assertThat(new ProductTeamTerms(ID, 2, 2399).configured()).isFalse();
        assertThat(new ProductTeamTerms(ID, 2, 86_401).configured()).isFalse();
        assertThat(new ProductTeamTerms(ID, null, 2400).configured()).isFalse();
        assertThat(new ProductTeamTerms(ID, 2, null).configured()).isFalse();
    }

    @Test
    void termsValueNeedsItsProduct() {
        assertThatThrownBy(() -> ProductTeamTerms.unset(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Reconstitution keeps stored terms; without them the product reads as never configured. */
    @Test
    void reconstitutionCarriesOrDefaultsTheTerms() {
        ProductTeamTerms stored = new ProductTeamTerms(ID, 3, 7200);
        assertThat(product(stored).teamTerms()).isEqualTo(stored);
        assertThat(product(null).teamTerms()).isEqualTo(ProductTeamTerms.unset(ID));
    }

    @Test
    void auditChangeRequiresItsFacts() {
        ProductTeamTerms before = ProductTeamTerms.unset(ID);
        ProductTeamTerms after = new ProductTeamTerms(ID, 3, 7200);
        TeamTermsChange change = new TeamTermsChange(ID, 1, before, after, null, ACTOR);
        assertThat(change.current()).isEqualTo(after);
        assertThatThrownBy(() -> new TeamTermsChange(null, 1, before, after, null, ACTOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TeamTermsChange(ID, 1, null, after, null, ACTOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TeamTermsChange(ID, 1, before, null, null, ACTOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TeamTermsChange(ID, 1, before, after, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Product product(ProductTeamTerms terms) {
        return Product.reconstitute(
                ID,
                "SKU-R",
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
                0,
                terms);
    }
}
