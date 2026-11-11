package com.josemorales.billetera.split;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Una cuenta dividida: alguien pagó (por ejemplo, la cena) y le cobra su parte a cada participante.
 * Regla: la suma de las partes de los deudores más la parte que asume el creador es exactamente el total.
 */
@Entity
@Table(name = "bill_split")
public class BillSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long creatorAccountId;

    @Column(nullable = false, length = 140)
    private String description;

    @Column(nullable = false)
    private long totalAmount;

    /** Lo que pone el propio creador (su consumo y, si aplica, los pesos sobrantes de redondear). */
    @Column(nullable = false)
    private long creatorShare;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SplitStatus status;

    @OneToMany(mappedBy = "split", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<SplitShare> shares = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected BillSplit() {}

    public BillSplit(Long creatorAccountId, String description, long totalAmount, long creatorShare) {
        this.creatorAccountId = creatorAccountId;
        this.description = description;
        this.totalAmount = totalAmount;
        this.creatorShare = creatorShare;
        this.status = SplitStatus.OPEN;
        this.createdAt = Instant.now();
    }

    public void addShare(Long debtorAccountId, long amount) {
        shares.add(new SplitShare(this, debtorAccountId, amount));
    }

    /** Se cierra cuando ya no queda ninguna parte pendiente. */
    public void refreshStatus() {
        boolean anyPending = shares.stream().anyMatch(s -> s.getStatus() == ShareStatus.PENDING);
        this.status = anyPending ? SplitStatus.OPEN : SplitStatus.COMPLETED;
    }

    public long collected() {
        return shares.stream().filter(s -> s.getStatus() == ShareStatus.PAID).mapToLong(SplitShare::getAmount).sum();
    }

    public long pending() {
        return shares.stream().filter(s -> s.getStatus() == ShareStatus.PENDING).mapToLong(SplitShare::getAmount).sum();
    }

    public Long getId() { return id; }
    public Long getCreatorAccountId() { return creatorAccountId; }
    public String getDescription() { return description; }
    public long getTotalAmount() { return totalAmount; }
    public long getCreatorShare() { return creatorShare; }
    public SplitStatus getStatus() { return status; }
    public List<SplitShare> getShares() { return shares; }
    public Instant getCreatedAt() { return createdAt; }
}
