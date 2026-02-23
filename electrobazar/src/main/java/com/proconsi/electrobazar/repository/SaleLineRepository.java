package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.SaleLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaleLineRepository extends JpaRepository<SaleLine, Long> {

    // Líneas de una venta concreta
    List<SaleLine> findBySaleId(Long saleId);

    // Cuántas veces se ha vendido un producto (para estadísticas)
    long countByProductId(Long productId);
}