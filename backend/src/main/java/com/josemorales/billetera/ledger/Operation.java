package com.josemorales.billetera.ledger;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Una operación de dinero (carga o transferencia). La llave de idempotencia es única:
 * si el cliente reintenta la misma petición, no se mueve la plata dos veces.
 */
@Entity
@Table(name = "operation")
public class Operation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String idempotencyKey;

    /** Huella de los datos de la petición, para detectar la misma llave usada con otros datos. */
    @Column(nullable = false, length = 300)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OperationType type;

    private Long fromAccountId;

    @Column(nullable = false)
    private Long toAccountId;

    @Column(nullable = false)
    private long amount;

    @Column(length = 140)
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Operation() {}

    public Operation(String idempotencyKey, String requestFingerprint, OperationType type,
                     Long fromAccountId, Long toAccountId, long amount, String description) {
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.type = type;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public OperationType getType() { return type; }
    public Long getFromAccountId() { return fromAccountId; }
    public Long getToAccountId() { return toAccountId; }
    public long getAmount() { return amount; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
