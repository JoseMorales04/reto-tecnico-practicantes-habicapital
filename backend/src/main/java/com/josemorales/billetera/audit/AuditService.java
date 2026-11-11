package com.josemorales.billetera.audit;

import com.josemorales.billetera.account.Account;
import com.josemorales.billetera.account.AccountRepository;
import com.josemorales.billetera.ledger.MovementRepository;
import com.josemorales.billetera.ledger.OperationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Revisa que no se haya perdido ni creado plata:
 * 1. La suma de todos los saldos es igual a la plata que entró por cargas (las transferencias solo la mueven).
 * 2. El saldo de cada cuenta es igual a la suma de su historial.
 */
@Service
public class AuditService {
    private final AccountRepository accounts;
    private final OperationRepository operations;
    private final MovementRepository movements;

    public AuditService(AccountRepository accounts, OperationRepository operations, MovementRepository movements) {
        this.accounts = accounts;
        this.operations = operations;
        this.movements = movements;
    }

    public record AccountMismatch(Long accountId, long balance, long ledgerSum) {}

    public record AuditReport(boolean ok, long totalBalances, long totalDeposits, List<AccountMismatch> mismatches) {}

    @Transactional(readOnly = true)
    public AuditReport run() {
        long totalBalances = accounts.sumAllBalances();
        long totalDeposits = operations.sumDeposits();
        List<AccountMismatch> mismatches = new ArrayList<>();
        for (Account a : accounts.findAll()) {
            long ledgerSum = movements.signedSumForAccount(a.getId());
            if (ledgerSum != a.getBalance()) {
                mismatches.add(new AccountMismatch(a.getId(), a.getBalance(), ledgerSum));
            }
        }
        boolean ok = totalBalances == totalDeposits && mismatches.isEmpty();
        return new AuditReport(ok, totalBalances, totalDeposits, mismatches);
    }
}
