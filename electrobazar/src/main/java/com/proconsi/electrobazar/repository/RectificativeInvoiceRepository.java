package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.AeatStatus;
import com.proconsi.electrobazar.model.RectificativeInvoice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RectificativeInvoiceRepository extends JpaRepository<RectificativeInvoice, Long> {

    @EntityGraph(attributePaths = {"saleReturn", "saleReturn.originalSale", "saleReturn.originalSale.customer"})
    @Query("SELECT r FROM RectificativeInvoice r WHERE " +
           "(:status IS NULL OR r.aeatStatus = :status) AND " +
           "(:reason IS NULL OR r.aeatRejectionReason = :reason) AND " +
           "(:start IS NULL OR r.createdAt >= :start) AND " +
           "(:end IS NULL OR r.createdAt <= :end)")
    Page<RectificativeInvoice> findByFilters(
            @Param("status") AeatStatus status,
            @Param("reason") com.proconsi.electrobazar.model.AeatRejectionReason reason,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    @EntityGraph(attributePaths = {"saleReturn", "saleReturn.originalSale", "saleReturn.originalSale.customer"})
    @Query("SELECT r FROM RectificativeInvoice r")
    Page<RectificativeInvoice> findAllPaginated(Pageable pageable);

    @EntityGraph(attributePaths = {"saleReturn", "saleReturn.originalSale", "originalInvoice", "originalTicket"})
    @Query("SELECT r FROM RectificativeInvoice r")
    List<RectificativeInvoice> findAllWithDetails(Sort sort);

    Optional<RectificativeInvoice> findBySaleReturnId(Long returnId);

    Optional<RectificativeInvoice> findFirstByOrderByCreatedAtDesc();

    Optional<RectificativeInvoice> findByHashCurrentInvoice(String hashCurrentInvoice);

    @EntityGraph(attributePaths = { "saleReturn" })
    @Query("SELECT r FROM RectificativeInvoice r WHERE r.aeatStatus = :pending " +
           "OR (r.aeatStatus = :rejected AND r.aeatRejectionReason = com.proconsi.electrobazar.model.AeatRejectionReason.NETWORK_ERROR)")
    List<RectificativeInvoice> findPendingAndRejected(@Param("pending") AeatStatus pending,
                                                       @Param("rejected") AeatStatus rejected);

    default List<RectificativeInvoice> findPendingSend() {
        return findPendingAndRejected(AeatStatus.PENDING_SEND, AeatStatus.REJECTED);
    }
}
