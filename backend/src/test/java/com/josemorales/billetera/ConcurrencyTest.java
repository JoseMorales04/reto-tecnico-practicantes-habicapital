package com.josemorales.billetera;

import com.josemorales.billetera.account.Account;
import com.josemorales.billetera.account.AccountService;
import com.josemorales.billetera.audit.AuditService;
import com.josemorales.billetera.ledger.IdempotentCall;
import com.josemorales.billetera.ledger.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de estrés: muchas operaciones al mismo tiempo, que es donde se pierde plata
 * en un sistema mal hecho (dos hilos leen el mismo saldo y ambos lo descuentan).
 */
@SpringBootTest
class ConcurrencyTest {

    @Autowired AccountService accountService;
    @Autowired LedgerService ledger;
    @Autowired AuditService audit;
    @Autowired IdempotentCall idempotent;
    @Autowired DatabaseCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner.clean();
    }

    @Test
    void milTransferenciasSimultaneasConservanLaPlataTotal() throws Exception {
        int accountsCount = 10;
        long initial = 100_000;
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < accountsCount; i++) {
            Account a = accountService.create("Cuenta " + i);
            ledger.deposit(a.getId(), initial, null, "dep-" + i);
            ids.add(a.getId());
        }

        int threads = 20;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int thread = t;
            futures.add(pool.submit(() -> {
                Random rnd = new Random(thread);
                start.await();
                for (int i = 0; i < perThread; i++) {
                    long from = ids.get(rnd.nextInt(accountsCount));
                    long to = ids.get(rnd.nextInt(accountsCount));
                    if (from == to) continue;
                    long amount = 1 + rnd.nextInt(30_000);
                    try {
                        ledger.transfer(from, to, amount, null, "t" + thread + "-" + i);
                        ok.incrementAndGet();
                    } catch (RuntimeException e) {
                        rejected.incrementAndGet(); // saldo insuficiente u otra regla: no debe mover plata
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(2, TimeUnit.MINUTES);
        pool.shutdown();

        AuditService.AuditReport report = audit.run();
        assertThat(report.totalBalances()).isEqualTo(accountsCount * initial);
        assertThat(report.mismatches()).isEmpty();
        assertThat(report.ok()).isTrue();
        for (Long id : ids) {
            assertThat(accountService.get(id).getBalance()).isNotNegative();
        }
        assertThat(ok.get()).isPositive();
    }

    @Test
    void elMismoSaldoNoSePuedeGastarDosVeces() throws Exception {
        Account ana = accountService.create("Ana");
        ledger.deposit(ana.getId(), 50_000, null, "dep-ana");
        List<Long> receivers = new ArrayList<>();
        for (int i = 0; i < 10; i++) receivers.add(accountService.create("Amigo " + i).getId());

        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final long to = receivers.get(i);
            final int n = i;
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    ledger.transfer(ana.getId(), to, 50_000, null, "gasto-" + n);
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
        assertThat(accountService.get(ana.getId()).getBalance()).isZero();
        assertThat(audit.run().ok()).isTrue();
    }

    @Test
    void reintentosSimultaneosConLaMismaLlaveSoloCobranUnaVez() throws Exception {
        Account ana = accountService.create("Ana");
        Account beto = accountService.create("Beto");
        ledger.deposit(ana.getId(), 100_000, null, "dep-ana");

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return idempotent.run("pago-unico",
                        () -> ledger.transfer(ana.getId(), beto.getId(), 10_000, "Doble clic", "pago-unico")).getId();
            }));
        }
        start.countDown();
        List<Long> operationIds = new ArrayList<>();
        for (Future<Long> f : futures) operationIds.add(f.get(1, TimeUnit.MINUTES));
        pool.shutdown();

        assertThat(operationIds).containsOnly(operationIds.get(0));
        assertThat(accountService.get(ana.getId()).getBalance()).isEqualTo(90_000);
        assertThat(accountService.get(beto.getId()).getBalance()).isEqualTo(10_000);
        assertThat(audit.run().ok()).isTrue();
    }
}
