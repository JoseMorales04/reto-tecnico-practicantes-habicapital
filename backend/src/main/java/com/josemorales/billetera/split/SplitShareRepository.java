package com.josemorales.billetera.split;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SplitShareRepository extends JpaRepository<SplitShare, Long> {

    /** Bloquea la parte: dos pagos simultáneos de la misma parte se atienden uno tras otro. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SplitShare s where s.id = :id")
    Optional<SplitShare> findByIdForUpdate(@Param("id") Long id);

    List<SplitShare> findByDebtorAccountIdAndStatusOrderByIdDesc(Long debtorAccountId, ShareStatus status);

    /** Partes pendientes donde tanto el deudor como quien cobra pertenecen al grupo. */
    @Query("""
            select s from SplitShare s join fetch s.split sp
            where s.status = com.josemorales.billetera.split.ShareStatus.PENDING
              and s.debtorAccountId in :accountIds
              and sp.creatorAccountId in :accountIds
            """)
    List<SplitShare> findPendingWithinGroup(@Param("accountIds") java.util.Collection<Long> accountIds);

    /** Partes marcadas como pagadas cuya transferencia no existe o no coincide en monto o cuentas. */
    @Query("""
            select count(s) from SplitShare s
            where s.status = com.josemorales.billetera.split.ShareStatus.PAID
              and not exists (
                select o from Operation o
                where o.id = s.operationId
                  and o.amount = s.amount
                  and o.fromAccountId = s.debtorAccountId
                  and o.toAccountId = s.split.creatorAccountId)
            """)
    long countPaidSharesWithoutMatchingTransfer();
}
