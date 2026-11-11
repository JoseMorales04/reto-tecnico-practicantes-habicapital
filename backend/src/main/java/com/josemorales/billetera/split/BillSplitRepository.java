package com.josemorales.billetera.split;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillSplitRepository extends JpaRepository<BillSplit, Long> {
    List<BillSplit> findByCreatorAccountIdOrderByIdDesc(Long creatorAccountId);
}
