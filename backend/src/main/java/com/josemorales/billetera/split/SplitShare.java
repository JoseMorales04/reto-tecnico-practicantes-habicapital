package com.josemorales.billetera.split;

import com.josemorales.billetera.common.BusinessException;
import jakarta.persistence.*;

import java.time.Instant;

/** La parte que le corresponde pagar a un participante de una cuenta dividida. */
@Entity
@Table(name = "split_share")
public class SplitShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "split_id")
    private BillSplit split;

    @Column(nullable = false)
    private Long debtorAccountId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShareStatus status;

    /** La transferencia con la que se pagó esta parte. */
    private Long operationId;

    private Instant resolvedAt;

    @Version
    private long version;

    protected SplitShare() {}

    SplitShare(BillSplit split, Long debtorAccountId, long amount) {
        this.split = split;
        this.debtorAccountId = debtorAccountId;
        this.amount = amount;
        this.status = ShareStatus.PENDING;
    }

    public void markPaid(Long operationId) {
        requirePending();
        this.status = ShareStatus.PAID;
        this.operationId = operationId;
        this.resolvedAt = Instant.now();
    }

    public void decline() {
        requirePending();
        this.status = ShareStatus.DECLINED;
        this.resolvedAt = Instant.now();
    }

    private void requirePending() {
        if (status != ShareStatus.PENDING) {
            throw BusinessException.conflict("SHARE_NOT_PENDING", "Esta parte ya está " + status);
        }
    }

    public Long getId() { return id; }
    public BillSplit getSplit() { return split; }
    public Long getDebtorAccountId() { return debtorAccountId; }
    public long getAmount() { return amount; }
    public ShareStatus getStatus() { return status; }
    public Long getOperationId() { return operationId; }
    public Instant getResolvedAt() { return resolvedAt; }
}
