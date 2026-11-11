package com.josemorales.billetera;

import com.josemorales.billetera.account.Account;
import com.josemorales.billetera.account.AccountService;
import com.josemorales.billetera.common.BusinessException;
import com.josemorales.billetera.settlement.SettlementService;
import com.josemorales.billetera.settlement.SettlementService.Payment;
import com.josemorales.billetera.settlement.SettlementService.Plan;
import com.josemorales.billetera.split.SplitDtos.CreateSplitRequest;
import com.josemorales.billetera.split.SplitService;
import com.josemorales.billetera.settlement.SettlementServiceTestAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SettlementServiceTest {

    @Autowired AccountService accountService;
    @Autowired SplitService splits;
    @Autowired SettlementService settlement;
    @Autowired DatabaseCleaner cleaner;

    Account ana;
    Account beto;
    Account caro;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        ana = accountService.create("Ana");
        beto = accountService.create("Beto");
        caro = accountService.create("Caro");
    }

    @Test
    void cuatroPartesPendientesSeLiquidanConDosTransferencias() {
        // Ana pagó la cena (90.000 entre los tres): Beto y Caro le deben 30.000 cada uno.
        splits.create(new CreateSplitRequest(ana.getId(), "Cena", 90_000L,
                List.of(beto.getId(), caro.getId()), true, null));
        // Beto pagó el taxi (30.000 entre los tres): Ana y Caro le deben 10.000 cada una.
        splits.create(new CreateSplitRequest(beto.getId(), "Taxi", 30_000L,
                List.of(ana.getId(), caro.getId()), true, null));

        Plan plan = settlement.plan(List.of(ana.getId(), beto.getId(), caro.getId()));

        assertThat(plan.pendingShares()).isEqualTo(4);
        assertThat(plan.netBalances()).containsEntry(ana.getId(), 50_000L)
                .containsEntry(beto.getId(), -10_000L)
                .containsEntry(caro.getId(), -40_000L);
        assertThat(plan.payments()).containsExactlyInAnyOrder(
                new Payment(caro.getId(), ana.getId(), 40_000L),
                new Payment(beto.getId(), ana.getId(), 10_000L));
    }

    @Test
    void elPlanDejaATodosExactamenteEnCero() {
        Map<Long, Long> net = new HashMap<>(Map.of(1L, 70_001L, 2L, -20_000L, 3L, -33_334L, 4L, -16_667L, 5L, 0L));

        List<Payment> payments = SettlementServiceTestAccess.simplify(net);

        Map<Long, Long> after = new HashMap<>(net);
        for (Payment p : payments) {
            after.merge(p.fromAccountId(), p.amount(), Long::sum);
            after.merge(p.toAccountId(), -p.amount(), Long::sum);
        }
        assertThat(after.values()).containsOnly(0L);
        assertThat(payments.size()).isLessThanOrEqualTo(3); // 4 personas con saldo distinto de cero -> máximo 3 pagos
    }

    @Test
    void lasPartesConGenteFueraDelGrupoNoCuentan() {
        Account dani = accountService.create("Dani");
        splits.create(new CreateSplitRequest(dani.getId(), "Mercado", 20_000L,
                List.of(ana.getId()), true, null));

        Plan plan = settlement.plan(List.of(ana.getId(), beto.getId()));

        assertThat(plan.pendingShares()).isZero();
        assertThat(plan.payments()).isEmpty();
    }

    @Test
    void validaElGrupo() {
        assertThatThrownBy(() -> settlement.plan(List.of(ana.getId()))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> settlement.plan(List.of(ana.getId(), 999L))).isInstanceOf(BusinessException.class);
    }
}
