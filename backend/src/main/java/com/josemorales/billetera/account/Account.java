package com.josemorales.billetera.account;

import com.josemorales.billetera.common.BusinessException;
import jakarta.persistence.*;
import org.hibernate.annotations.Check;

import java.time.Instant;

@Entity
@Table(name = "account")
@Check(constraints = "balance >= 0") // la base de datos también impide saldos negativos
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    /** Saldo en pesos enteros. Solo cambia con credit/debit, dentro de una transacción con bloqueo. */
    @Column(nullable = false)
    private long balance;

    @Version
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {}

    public Account(String name) {
        this.name = name;
        this.balance = 0;
        this.createdAt = Instant.now();
    }

    public void credit(long amount) {
        // addExact lanza excepción si hay desbordamiento en vez de dar un saldo negativo silencioso
        this.balance = Math.addExact(this.balance, amount);
    }

    public void debit(long amount) {
        if (amount > this.balance) {
            throw BusinessException.conflict("INSUFFICIENT_FUNDS", "Saldo insuficiente en la cuenta " + id);
        }
        this.balance = this.balance - amount;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public long getBalance() { return balance; }
    public Instant getCreatedAt() { return createdAt; }
}
