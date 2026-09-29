package com.blocbuy.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.PricingMode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductStatus;
import com.blocbuy.catalog.model.ProductTeamTerms;
import com.blocbuy.catalog.model.TeamTermsChange;
import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.repository.ProductRepository;
import com.blocbuy.catalog.service.TeamPriceAppService.ConfigurePolicy;
import com.blocbuy.catalog.service.TeamTermsAppService.ConfigureTerms;
import com.blocbuy.catalog.service.TeamTermsAppService.TeamTermsResult;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.service.UserAppService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Orchestration proofs; the real HTTP and database behaviour is in TeamTermsFlowIT. */
@ExtendWith(MockitoExtension.class)
class TeamTermsAppServiceImplTest {
    private static final UUID ID = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant UNTIL = Instant.parse("2026-11-01T00:00:00Z");

    @Mock private ProductRepository products;
    @Mock private TeamPriceAppService prices;
    @Mock private UserAppService users;
    private TeamTermsAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TeamTermsAppServiceImpl(products, prices, users);
    }

    /** Lock, account, version, rules, write, audit — and the price policy is never touched. */
    @Test
    void termsOnlyWriteFollowsTheProductFirstProtocol() {
        when(products.lockById(ID)).thenReturn(Optional.of(product(0)));

        TeamTermsResult result = service.configure(ID, new ConfigureTerms(0, 3, 7200, null), ACTOR);

        assertThat(result.teamPrice()).isNull();
        assertThat(result.product().version()).isEqualTo(1);
        assertThat(result.product().teamTerms()).isEqualTo(new ProductTeamTerms(ID, 3, 7200));
        var order = inOrder(products, users);
        order.verify(products).lockById(ID);
        order.verify(users).requireActive(ACTOR);
        order.verify(products).updateTeamTerms(result.product(), 0, ACTOR);
        order.verify(products).appendTeamTermsLog(any());
        verifyNoInteractions(prices);
    }

    /** The audit row records the terms before and after, the new revision and the actor. */
    @Test
    void auditRowCapturesBeforeAndAfter() {
        Product configured = product(0).configureTeamTerms(2, 2400, null);
        when(products.lockById(ID)).thenReturn(Optional.of(configured));

        service.configure(ID, new ConfigureTerms(1, 5, 3600, null), ACTOR);

        ArgumentCaptor<TeamTermsChange> logged = ArgumentCaptor.forClass(TeamTermsChange.class);
        verify(products).appendTeamTermsLog(logged.capture());
        assertThat(logged.getValue())
                .isEqualTo(
                        new TeamTermsChange(
                                ID,
                                2,
                                new ProductTeamTerms(ID, 2, 2400),
                                new ProductTeamTerms(ID, 5, 3600),
                                null,
                                ACTOR));
    }

    /** The price policy is appended after the terms, in the same transaction, and audited. */
    @Test
    void teamPriceIsAppendedAfterTheTermsAndItsVersionAudited() {
        when(products.lockById(ID)).thenReturn(Optional.of(product(0)));
        ConfigurePolicy price = price("8.00");
        TeamPricePolicy policy =
                new TeamPricePolicy(ID, new BigDecimal("8.00"), "sgd", FROM, UNTIL, 4);
        when(prices.configure(ID, ACTOR, price)).thenReturn(policy);

        TeamTermsResult result =
                service.configure(ID, new ConfigureTerms(0, 3, 7200, price), ACTOR);

        assertThat(result.teamPrice()).isEqualTo(policy);
        var order = inOrder(products, prices);
        order.verify(products).updateTeamTerms(result.product(), 0, ACTOR);
        order.verify(prices).configure(ID, ACTOR, price);
        ArgumentCaptor<TeamTermsChange> logged = ArgumentCaptor.forClass(TeamTermsChange.class);
        order.verify(products).appendTeamTermsLog(logged.capture());
        assertThat(logged.getValue().teamPriceVersion()).isEqualTo(4);
    }

    /** A stale client learns to re-read before anything is validated or written. */
    @Test
    void staleVersionIsRejectedBeforeAnyWrite() {
        when(products.lockById(ID)).thenReturn(Optional.of(product(2)));
        ConfigureTerms invalidAndStale = new ConfigureTerms(1, 1, 1, null);

        assertThatThrownBy(() -> service.configure(ID, invalidAndStale, ACTOR))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.PRODUCT_VERSION_CONFLICT));
        verify(products, never()).updateTeamTerms(any(), anyInt(), any());
        verifyNoInteractions(prices);
    }

    @Test
    void missingProductIsNotFound() {
        when(products.lockById(ID)).thenReturn(Optional.empty());
        ConfigureTerms command = new ConfigureTerms(0, 3, 7200, null);

        assertThatThrownBy(() -> service.configure(ID, command, ACTOR))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND));
        verifyNoInteractions(users, prices);
    }

    /** The account check follows the lock, like every admin product command. */
    @Test
    void disabledActorIsRejectedBeforeTheVersionCheck() {
        when(products.lockById(ID)).thenReturn(Optional.of(product(0)));
        doThrow(new BizException(IdentityErrorCode.ACCOUNT_DISABLED))
                .when(users)
                .requireActive(ACTOR);
        ConfigureTerms command = new ConfigureTerms(9, 3, 7200, null);

        assertThatThrownBy(() -> service.configure(ID, command, ACTOR))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(IdentityErrorCode.ACCOUNT_DISABLED));
        verify(products, never()).updateTeamTerms(any(), anyInt(), any());
    }

    /** Rule violations write nothing and name every field. */
    @Test
    void invalidTermsAreRejectedWithFieldsAndNothingIsWritten() {
        when(products.lockById(ID)).thenReturn(Optional.of(product(0)));
        ConfigureTerms command = new ConfigureTerms(0, 1, 10, price("10.00"));

        assertThatThrownBy(() -> service.configure(ID, command, ACTOR))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex -> {
                            assertThat(ex.errorCode())
                                    .isEqualTo(CatalogErrorCode.TEAM_TERMS_INVALID);
                            assertThat(ex.details())
                                    .containsOnlyKeys(
                                            "targetSize", "lifetimeSeconds", "teamPrice.amount");
                        });
        verify(products, never()).updateTeamTerms(any(), anyInt(), any());
        verify(products, never()).appendTeamTermsLog(any());
        verifyNoInteractions(prices);
    }

    /** A policy the price service rejects propagates, so the transaction rolls back with it. */
    @Test
    void rejectedPolicyPropagatesAndIsNotAudited() {
        when(products.lockById(ID)).thenReturn(Optional.of(product(0)));
        ConfigurePolicy price = price("8.00");
        when(prices.configure(ID, ACTOR, price))
                .thenThrow(new BizException(CatalogErrorCode.TEAM_PRICE_VERSION_CONFLICT));
        ConfigureTerms command = new ConfigureTerms(0, 3, 7200, price);

        assertThatThrownBy(() -> service.configure(ID, command, ACTOR))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_VERSION_CONFLICT));
        verify(products, never()).appendTeamTermsLog(any());
    }

    private static ConfigurePolicy price(String amount) {
        return new ConfigurePolicy(new BigDecimal(amount), "sgd", FROM, UNTIL, 0);
    }

    private static Product product(int version) {
        return Product.reconstitute(
                ID,
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
                version);
    }
}
