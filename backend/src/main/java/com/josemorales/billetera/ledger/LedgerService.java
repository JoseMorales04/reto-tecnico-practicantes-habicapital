package com.josemorales.billetera.ledger;

import com.josemorales.billetera.account.Account;
import com.josemorales.billetera.account.AccountRepository;
import com.josemorales.billetera.common.BusinessException;
import com.josemorales.billetera.common.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Único lugar donde cambia la plata. Cada operación es una transacción:
 * o se aplica completa (saldos + historial + registro de la operación) o no se aplica nada.
 */
@Service
public class LedgerService {
    private final AccountRepository accounts;
    private final OperationRepository operations;
    private final MovementRepository movements;

    public LedgerService(AccountRepository accounts, OperationRepository operations, MovementRepository movements) {
        this.accounts = accounts;
        this.operations = operations;
        this.movements = movements;
    }

    @Transactional
    public Operation deposit(Long accountId, long amount, String description, String idempotencyKey) {
        Money.requireValidAmount(amount);
        String fingerprint = "DEPOSIT|" + accountId + "|" + amount + "|" + nullToEmpty(description);
        Optional<Operation> previous = findPrevious(idempotencyKey, fingerprint);
        if (previous.isPresent()) {
            return previous.get();
        }

        // Se registra primero la operación: si llega otra petición con la misma llave
        // al mismo tiempo, la restricción única hace fallar a una de las dos antes de mover plata.
        Operation op = operations.saveAndFlush(new Operation(idempotencyKey, fingerprint, OperationType.DEPOSIT,
                null, accountId, amount, description));

        Account account = lock(accountId);
        account.credit(amount);
        movements.save(new Movement(accountId, op.getId(), MovementType.DEPOSIT, amount,
                account.getBalance(), null, description));
        return op;
    }

    @Transactional
    public Operation transfer(Long fromId, Long toId, long amount, String description, String idempotencyKey) {
        Money.requireValidAmount(amount);
        if (fromId.equals(toId)) {
            throw BusinessException.badRequest("SAME_ACCOUNT", "No puedes transferirte a la misma cuenta");
        }
        String fingerprint = "TRANSFER|" + fromId + "|" + toId + "|" + amount + "|" + nullToEmpty(description);
        Optional<Operation> previous = findPrevious(idempotencyKey, fingerprint);
        if (previous.isPresent()) {
            return previous.get();
        }

        Operation op = operations.saveAndFlush(new Operation(idempotencyKey, fingerprint, OperationType.TRANSFER,
                fromId, toId, amount, description));

        // Se bloquean las dos cuentas siempre en el mismo orden (id menor primero).
        // Así dos transferencias cruzadas (A->B y B->A) no se quedan esperándose para siempre (deadlock).
        Account first = lock(Math.min(fromId, toId));
        Account second = lock(Math.max(fromId, toId));
        Account from = first.getId().equals(fromId) ? first : second;
        Account to = first.getId().equals(toId) ? first : second;

        from.debit(amount); // lanza INSUFFICIENT_FUNDS y la transacción completa se revierte
        to.credit(amount);

        movements.save(new Movement(fromId, op.getId(), MovementType.TRANSFER_OUT, amount,
                from.getBalance(), toId, description));
        movements.save(new Movement(toId, op.getId(), MovementType.TRANSFER_IN, amount,
                to.getBalance(), fromId, description));
        return op;
    }

    @Transactional(readOnly = true)
    public List<Movement> history(Long accountId) {
        if (!accounts.existsById(accountId)) {
            throw BusinessException.notFound("La cuenta " + accountId);
        }
        return movements.findByAccountIdOrderByIdDesc(accountId);
    }

    /** Busca una operación ya hecha con esa llave. Misma llave con otros datos es un error del cliente. */
    @Transactional(readOnly = true)
    public Optional<Operation> findPrevious(String idempotencyKey, String fingerprint) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw BusinessException.badRequest("MISSING_IDEMPOTENCY_KEY", "Falta la llave de idempotencia");
        }
        Optional<Operation> previous = operations.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent() && !previous.get().getRequestFingerprint().equals(fingerprint)) {
            throw BusinessException.conflict("IDEMPOTENCY_KEY_REUSED",
                    "Esa llave de idempotencia ya se usó con otros datos");
        }
        return previous;
    }

    private Account lock(Long id) {
        return accounts.findByIdForUpdate(id).orElseThrow(() -> BusinessException.notFound("La cuenta " + id));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
