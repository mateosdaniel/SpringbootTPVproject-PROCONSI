package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.CashWithdrawal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CashWithdrawalRepository extends JpaRepository<CashWithdrawal, Long> {
    List<CashWithdrawal> findByCashRegisterId(Long registerId);
}
