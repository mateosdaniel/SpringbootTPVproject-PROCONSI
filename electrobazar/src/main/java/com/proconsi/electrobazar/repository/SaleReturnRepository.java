package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.SaleReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long> {

    /** All returns for a given original sale. */
    List<SaleReturn> findByOriginalSaleId(Long saleId);
}
