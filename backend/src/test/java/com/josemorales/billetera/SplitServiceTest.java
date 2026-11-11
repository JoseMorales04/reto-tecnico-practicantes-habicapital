package com.josemorales.billetera;

import com.josemorales.billetera.account.Account;
import com.josemorales.billetera.account.AccountService;
import com.josemorales.billetera.audit.AuditService;
import com.josemorales.billetera.common.BusinessException;
import com.josemorales.billetera.ledger.LedgerService;
import com.josemorales.billetera.split.BillSplit;
import com.josemorales.billetera.split.SplitDtos.CreateSplitRequest;
import com.josemorales.billetera.split.SplitDtos.ShareInput;
import com.josemorales.billetera.split.SplitService;
import com.josemorales.billetera.split.SplitShare;
import com.josemorales.billetera.split.SplitStatus;
import com.josemorales.billetera.split.ShareStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SplitServiceTest {

    @Autowired AccountService accountService;
    @Autowired LedgerService ledger;
    @Autowired SplitService splits;
    @Autowired AuditService audit;
    @Autowired DatabaseCleaner cleaner;

    Account ana;   // pagó la cena
    Account beto;
    Account caro;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        ana = accountService.create("Ana");
        beto = accountService.create("Beto");
        caro = accountService.create("Caro");
        ledger.deposit(beto.getId(), 100_000, null, "dep-beto");
        ledger.deposit(caro.getId(), 100_000, null, "dep-caro");
    }

    private long balance(Account a) {
        return accountService.get(a.getId()).getBalance();
    }

    private BillSplit equalSplit(long total, boolean includeCreator) {
        return splits.create(new CreateSplitRequest(ana.getId(), "Cena de cumpleaños", total,
                List.of(beto.getId(), caro.getId()), includeCreator, null));
    }

    private Long shareOf(BillSplit split, Account debtor) {
        return splits.get(split.getId()).getShares().stream()
                .filter(s -> s.getDebtorAccountId().equals(debtor.getId()))
                .findFirst().orElseThrow().getId();
    }

    @Test
    void dividirEnPartesIgualesNoPierdeNiCreaPesos() {
        // 100.000 entre 3 no da exacto: cada deudor paga 33.333 y Ana asume 33.334.
        BillSplit split = splits.get(equalSplit(100_000, true).getId());

        assertThat(split.getShares()).extracting(SplitShare::getAmount).containsOnly(33_333L);
        assertThat(split.getCreatorShare()).isEqualTo(33_334);
        long sum = split.getShares().stream().mapToLong(SplitShare::getAmount).sum() + split.getCreatorShare();
        assertThat(sum).isEqualTo(100_000);
    }

    @Test
    void siElCreadorNoParticipaLosPesosSobrantesSeRepartenEntreLosDeudores() {
        // 100.001 entre 2: uno paga 50.001 y el otro 50.000.
        BillSplit split = splits.get(equalSplit(100_001, false).getId());

        assertThat(split.getCreatorShare()).isZero();
        assertThat(split.getShares()).extracting(SplitShare::getAmount).containsExactlyInAnyOrder(50_001L, 50_000L);
    }

    @Test
    void elPesoDeMasRotaEntreLasMismasPersonas() {
        // Cuatro cenas de 100.000 entre los mismos tres: el peso que sobra rota Ana -> Beto -> Caro -> Ana.
        List<Long> quienPusoElPeso = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            BillSplit split = splits.get(equalSplit(100_000, true).getId());
            long sum = split.getShares().stream().mapToLong(SplitShare::getAmount).sum() + split.getCreatorShare();
            assertThat(sum).isEqualTo(100_000);

            if (split.getCreatorRoundingExtra() == 1) {
                quienPusoElPeso.add(ana.getId());
            }
            split.getShares().stream().filter(sh -> sh.getRoundingExtra() == 1)
                    .forEach(sh -> quienPusoElPeso.add(sh.getDebtorAccountId()));
        }
        assertThat(quienPusoElPeso).containsExactly(ana.getId(), beto.getId(), caro.getId(), ana.getId());
    }

    @Test
    void partesPersonalizadas() {
        BillSplit split = splits.get(splits.create(new CreateSplitRequest(ana.getId(), "Mercado", 90_000L, null, null,
                List.of(new ShareInput(beto.getId(), 50_000L), new ShareInput(caro.getId(), 25_000L)))).getId());

        assertThat(split.getCreatorShare()).isEqualTo(15_000);
        assertThatThrownBy(() -> splits.create(new CreateSplitRequest(ana.getId(), "Mal", 10_000L, null, null,
                List.of(new ShareInput(beto.getId(), 8_000L), new ShareInput(caro.getId(), 8_000L)))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("más que el total");
    }

    @Test
    void pagarUnaParteTransfiereYCierraLaDivisionAlFinal() {
        BillSplit split = equalSplit(60_000, true); // 20.000 cada uno

        splits.payShare(shareOf(split, beto), "pago-beto");
        assertThat(balance(beto)).isEqualTo(80_000);
        assertThat(balance(ana)).isEqualTo(20_000);
        assertThat(splits.get(split.getId()).getStatus()).isEqualTo(SplitStatus.OPEN);

        splits.payShare(shareOf(split, caro), "pago-caro");
        BillSplit done = splits.get(split.getId());
        assertThat(done.getStatus()).isEqualTo(SplitStatus.COMPLETED);
        assertThat(done.collected()).isEqualTo(40_000);
        assertThat(balance(ana)).isEqualTo(40_000);
        assertThat(audit.run().ok()).isTrue();
    }

    @Test
    void pagarDosVecesLaMismaParteSoloCobraUnaVez() {
        BillSplit split = equalSplit(60_000, true);
        Long share = shareOf(split, beto);

        splits.payShare(share, "pago-beto");
        splits.payShare(share, "pago-beto"); // reintento: responde igual, no cobra
        assertThatThrownBy(() -> splits.payShare(share, "otra-llave"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ya fue pagada");

        assertThat(balance(beto)).isEqualTo(80_000);
        assertThat(balance(ana)).isEqualTo(20_000);
        assertThat(audit.run().ok()).isTrue();
    }

    @Test
    void sinSaldoLaParteSiguePendienteYNoSeMueveNada() {
        Account dani = accountService.create("Dani"); // sin saldo
        BillSplit split = splits.create(new CreateSplitRequest(ana.getId(), "Pizza", 30_000L,
                List.of(dani.getId()), true, null));
        Long share = shareOf(split, dani);

        assertThatThrownBy(() -> splits.payShare(share, "pago-dani"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Saldo insuficiente");

        assertThat(splits.get(split.getId()).getShares().get(0).getStatus()).isEqualTo(ShareStatus.PENDING);
        assertThat(balance(ana)).isZero();
        assertThat(audit.run().ok()).isTrue();

        // Cuando Dani carga saldo, puede pagar con una llave nueva.
        ledger.deposit(dani.getId(), 20_000, null, "dep-dani");
        splits.payShare(share, "pago-dani-2");
        assertThat(balance(ana)).isEqualTo(15_000);
    }

    @Test
    void rechazarUnaParteLaCierraSinMoverPlata() {
        BillSplit split = equalSplit(60_000, true);
        splits.declineShare(shareOf(split, beto));

        assertThatThrownBy(() -> splits.payShare(shareOf(split, beto), "pago-beto"))
                .isInstanceOf(BusinessException.class);
        assertThat(balance(beto)).isEqualTo(100_000);
    }

    @Test
    void validaciones() {
        assertThatThrownBy(() -> splits.create(new CreateSplitRequest(ana.getId(), "X", 10_000L,
                List.of(ana.getId()), true, null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> splits.create(new CreateSplitRequest(ana.getId(), "X", 10_000L,
                List.of(beto.getId(), beto.getId()), true, null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> splits.create(new CreateSplitRequest(ana.getId(), "X", 2L,
                List.of(beto.getId(), caro.getId()), true, null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> splits.create(new CreateSplitRequest(ana.getId(), "X", 10_000L,
                List.of(), true, null))).isInstanceOf(BusinessException.class);
    }

    @Test
    void pagosSimultaneosDeLaMismaParteSoloCobranUnaVez() throws Exception {
        BillSplit split = equalSplit(60_000, true);
        Long share = shareOf(split, beto);

        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            final int n = i;
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    splits.payShare(share, "clic-" + n); // cada clic con su propia llave
                    ok.incrementAndGet();
                } catch (RuntimeException ignored) {
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(1, TimeUnit.MINUTES);
        pool.shutdown();

        assertThat(ok.get()).isEqualTo(1);
        assertThat(balance(beto)).isEqualTo(80_000);
        assertThat(balance(ana)).isEqualTo(20_000);
        assertThat(audit.run().ok()).isTrue();
    }
}
