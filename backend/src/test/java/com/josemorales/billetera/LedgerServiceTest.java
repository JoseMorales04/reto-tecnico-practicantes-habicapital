package com.josemorales.billetera;

import com.josemorales.billetera.account.Account;
import com.josemorales.billetera.account.AccountService;
import com.josemorales.billetera.audit.AuditService;
import com.josemorales.billetera.common.BusinessException;
import com.josemorales.billetera.ledger.LedgerService;
import com.josemorales.billetera.ledger.Movement;
import com.josemorales.billetera.ledger.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LedgerServiceTest {

    @Autowired AccountService accountService;
    @Autowired LedgerService ledger;
    @Autowired AuditService audit;
    @Autowired DatabaseCleaner cleaner;

    Account ana;
    Account beto;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        ana = accountService.create("Ana");
        beto = accountService.create("Beto");
    }

    private long balance(Account a) {
        return accountService.get(a.getId()).getBalance();
    }

    @Test
    void cargarSaldoAumentaElSaldoYQuedaEnElHistorial() {
        ledger.deposit(ana.getId(), 50_000, "Carga inicial", "dep-1");

        assertThat(balance(ana)).isEqualTo(50_000);
        List<Movement> history = ledger.history(ana.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getBalanceAfter()).isEqualTo(50_000);
    }

    @Test
    void transferirMuevePlataEntreCuentasSinCrearNiDestruir() {
        ledger.deposit(ana.getId(), 100_000, null, "dep-1");

        ledger.transfer(ana.getId(), beto.getId(), 30_000, "Cena", "tr-1");

        assertThat(balance(ana)).isEqualTo(70_000);
        assertThat(balance(beto)).isEqualTo(30_000);
        assertThat(audit.run().ok()).isTrue();
    }

    @Test
    void saldoInsuficienteNoMueveNada() {
        ledger.deposit(ana.getId(), 10_000, null, "dep-1");

        assertThatThrownBy(() -> ledger.transfer(ana.getId(), beto.getId(), 10_001, null, "tr-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Saldo insuficiente");

        assertThat(balance(ana)).isEqualTo(10_000);
        assertThat(balance(beto)).isZero();
        assertThat(ledger.history(beto.getId())).isEmpty();
        assertThat(audit.run().ok()).isTrue();
    }

    @Test
    void laMismaPeticionRepetidaSoloSeAplicaUnaVez() {
        ledger.deposit(ana.getId(), 100_000, null, "dep-1");

        Operation first = ledger.transfer(ana.getId(), beto.getId(), 20_000, "Arriendo", "tr-1");
        Operation retry = ledger.transfer(ana.getId(), beto.getId(), 20_000, "Arriendo", "tr-1");

        assertThat(retry.getId()).isEqualTo(first.getId());
        assertThat(balance(ana)).isEqualTo(80_000);
        assertThat(balance(beto)).isEqualTo(20_000);
    }

    @Test
    void reusarUnaLlaveConOtrosDatosEsRechazado() {
        ledger.deposit(ana.getId(), 100_000, null, "dep-1");
        ledger.transfer(ana.getId(), beto.getId(), 20_000, null, "tr-1");

        assertThatThrownBy(() -> ledger.transfer(ana.getId(), beto.getId(), 99_000, null, "tr-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("otros datos");
        assertThat(balance(ana)).isEqualTo(80_000);
    }

    @Test
    void montosInvalidosSonRechazados() {
        assertThatThrownBy(() -> ledger.deposit(ana.getId(), 0, null, "d0")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ledger.deposit(ana.getId(), -5_000, null, "d1")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ledger.deposit(ana.getId(), 10_000_001, null, "d2")).isInstanceOf(BusinessException.class);
        assertThat(balance(ana)).isZero();
    }

    @Test
    void noSePuedeTransferirALaMismaCuenta() {
        ledger.deposit(ana.getId(), 10_000, null, "dep-1");
        assertThatThrownBy(() -> ledger.transfer(ana.getId(), ana.getId(), 1_000, null, "tr-1"))
                .isInstanceOf(BusinessException.class);
        assertThat(balance(ana)).isEqualTo(10_000);
    }

    @Test
    void transferirACuentaInexistenteNoDescuentaNada() {
        ledger.deposit(ana.getId(), 10_000, null, "dep-1");
        assertThatThrownBy(() -> ledger.transfer(ana.getId(), 999L, 1_000, null, "tr-1"))
                .isInstanceOf(BusinessException.class);
        assertThat(balance(ana)).isEqualTo(10_000);
        assertThat(audit.run().ok()).isTrue();
    }
}
