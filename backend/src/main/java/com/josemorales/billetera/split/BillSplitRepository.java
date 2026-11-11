package com.josemorales.billetera.split;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BillSplitRepository extends JpaRepository<BillSplit, Long> {
    List<BillSplit> findByCreatorAccountIdOrderByIdDesc(Long creatorAccountId);

    /** Cuántos pesos extra por redondeo ha puesto cada cuenta como creadora: filas [accountId, total]. */
    @Query("""
            select b.creatorAccountId, sum(b.creatorRoundingExtra) from BillSplit b
            where b.creatorAccountId in :accountIds group by b.creatorAccountId
            """)
    List<Object[]> sumRoundingExtrasAsCreator(@Param("accountIds") Collection<Long> accountIds);
}
