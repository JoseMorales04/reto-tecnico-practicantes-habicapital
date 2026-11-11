package com.josemorales.billetera.ledger;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Si dos peticiones con la misma llave llegan al mismo tiempo, una gana y la otra falla
 * por la restricción única (su transacción se revierte sin mover plata).
 * Aquí, fuera de la transacción, se le devuelve a la perdedora el resultado de la ganadora.
 */
@Component
public class IdempotentCall {
    private static final int LOOKUP_ATTEMPTS = 20;
    private static final long LOOKUP_WAIT_MS = 100;

    private final OperationRepository operations;

    public IdempotentCall(OperationRepository operations) {
        this.operations = operations;
    }

    public Operation run(String idempotencyKey, Supplier<Operation> action) {
        try {
            return action.get();
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            return waitForWinner(idempotencyKey, e);
        }
    }

    /** La ganadora puede no haber confirmado todavía su transacción, así que se espera un poco. */
    private Operation waitForWinner(String idempotencyKey, DataAccessException original) {
        for (int i = 0; i < LOOKUP_ATTEMPTS; i++) {
            Optional<Operation> winner = operations.findByIdempotencyKey(idempotencyKey);
            if (winner.isPresent()) {
                return winner.get();
            }
            try {
                Thread.sleep(LOOKUP_WAIT_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw original; // no fue un choque de llaves: el error era otro y debe verse
    }
}
