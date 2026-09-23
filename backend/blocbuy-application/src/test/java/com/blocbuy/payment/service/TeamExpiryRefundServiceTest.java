package com.blocbuy.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.payment.gateway.PaymentBusinessPort;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class TeamExpiryRefundServiceTest {
    private static final UUID DEAL = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private final CheckoutPaymentRepository repository = mock(CheckoutPaymentRepository.class);
    private final PaymentBusinessPort business = mock(PaymentBusinessPort.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final TeamExpiryRefundService service =
            new TeamExpiryRefundService(repository, business, transactions);

    TeamExpiryRefundServiceTest() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void locksTeamBeforeRecordingAllRefundObligations() {
        service.recordExpiredTeam(DEAL);

        var ordered = inOrder(business, repository);
        ordered.verify(business).lockExpiredTeam(DEAL);
        ordered.verify(repository)
                .requireRefundsForExpiredTeam(DEAL, "TEAM_EXPIRED", "team-expired-" + DEAL);
        verify(transactions).commit(any());
    }
}
