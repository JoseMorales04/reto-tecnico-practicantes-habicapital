package com.josemorales.billetera.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovementRepository extends JpaRepository<Movement, Long> {
    List<Movement> findByAccountIdOrderByIdDesc(Long accountId);

    /** Suma con signo de los movimientos de una cuenta: debe coincidir con su saldo. */
    @Query("""
            select coalesce(sum(case when m.type = com.josemorales.billetera.ledger.MovementType.TRANSFER_OUT
                                     then -m.amount else m.amount end), 0)
            from Movement m where m.accountId = :accountId
            """)
    long signedSumForAccount(@Param("accountId") Long accountId);
}
