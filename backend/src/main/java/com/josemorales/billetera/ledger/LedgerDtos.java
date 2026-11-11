package com.josemorales.billetera.ledger;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class LedgerDtos {
    private LedgerDtos() {}

    public record DepositRequest(@NotNull @Positive Long amount, @Size(max = 140) String description) {}

    public record TransferRequest(@NotNull Long fromAccountId, @NotNull Long toAccountId,
                                  @NotNull @Positive Long amount, @Size(max = 140) String description) {}

    public record OperationResponse(Long id, String type, Long fromAccountId, Long toAccountId,
                                    long amount, String description, Instant createdAt) {
        public static OperationResponse from(Operation o) {
            return new OperationResponse(o.getId(), o.getType().name(), o.getFromAccountId(),
                    o.getToAccountId(), o.getAmount(), o.getDescription(), o.getCreatedAt());
        }
    }

    public record MovementResponse(Long id, String type, long amount, long balanceAfter,
                                   Long counterpartyAccountId, String description, Long operationId,
                                   Instant createdAt) {
        public static MovementResponse from(Movement m) {
            return new MovementResponse(m.getId(), m.getType().name(), m.getAmount(), m.getBalanceAfter(),
                    m.getCounterpartyAccountId(), m.getDescription(), m.getOperationId(), m.getCreatedAt());
        }
    }
}
