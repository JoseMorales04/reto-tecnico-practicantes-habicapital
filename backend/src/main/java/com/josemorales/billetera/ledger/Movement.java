package com.josemorales.billetera.ledger;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Una línea del historial de una cuenta. Nunca se modifica ni se borra:
 * el saldo de cada cuenta debe ser igual a la suma de sus movimientos.
 */
@Entity
// Índice por cuenta: el historial y la auditoría siempre filtran por accountId.
@Table(name = "movement", indexes = @Index(name = "idx_movement_account", columnList = "accountId"))
public class Movement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private Long operationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    /** Siempre positivo; el tipo dice si entra o sale. */
    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long balanceAfter;

    private Long counterpartyAccountId;

    @Column(length = 140)
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Movement() {}

    public Movement(Long accountId, Long operationId, MovementType type, long amount, long balanceAfter,
                    Long counterpartyAccountId, String description) {
        this.accountId = accountId;
        this.operationId = operationId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.counterpartyAccountId = counterpartyAccountId;
        this.description = description;
        this.createdAt = Instant.now();
    }

    /** Monto con signo: positivo si entra plata, negativo si sale. */
    public long signedAmount() {
        return type == MovementType.TRANSFER_OUT ? -amount : amount;
    }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public Long getOperationId() { return operationId; }
    public MovementType getType() { return type; }
    public long getAmount() { return amount; }
    public long getBalanceAfter() { return balanceAfter; }
    public Long getCounterpartyAccountId() { return counterpartyAccountId; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
