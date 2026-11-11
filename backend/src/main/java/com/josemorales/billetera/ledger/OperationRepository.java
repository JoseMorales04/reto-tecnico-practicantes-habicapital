package com.josemorales.billetera.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface OperationRepository extends JpaRepository<Operation, Long> {
    Optional<Operation> findByIdempotencyKey(String idempotencyKey);

    @Query("select coalesce(sum(o.amount), 0) from Operation o where o.type = com.josemorales.billetera.ledger.OperationType.DEPOSIT")
    long sumDeposits();
}
