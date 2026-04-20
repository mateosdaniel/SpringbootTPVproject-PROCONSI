package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.AeatStatus;
import com.proconsi.electrobazar.model.RectificativeInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RectificativeInvoiceRepository extends JpaRepository<RectificativeInvoice, Long> {

    Optional<RectificativeInvoice> findBySaleReturnId(Long returnId);

    Optional<RectificativeInvoice> findFirstByOrderByCreatedAtDesc();

    Optional<RectificativeInvoice> findByHashCurrentInvoice(String hashCurrentInvoice);

    @Query("SELECT r FROM RectificativeInvoice r WHERE r.aeatStatus = :status AND r.aeatRetryCount < :maxRetries")
    List<RectificativeInvoice> findPendingSend(@Param("maxRetries") int maxRetries,
                                               @Param("status") AeatStatus status);

    default List<RectificativeInvoice> findPendingSend(int maxRetries) {
        return findPendingSend(maxRetries, AeatStatus.PENDING_SEND);
    }
}
