package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.AeatStatus;
import com.proconsi.electrobazar.model.Invoice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @EntityGraph(attributePaths = { "sale", "sale.lines", "sale.lines.product", "sale.customer" })
    @Query("SELECT i FROM Invoice i WHERE i.sale.id = :saleId")
    Optional<Invoice> findBySaleId(@Param("saleId") Long saleId);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    Optional<Invoice> findFirstBySerieOrderByYearDescSequenceNumberDesc(String serie);

    List<Invoice> findBySerieOrderByYearAscSequenceNumberAsc(String serie);

    /** Para construir el bloque RegistroAnterior: busca la factura cuyo hash actual es el previo del actual. */
    Optional<Invoice> findByHashCurrentInvoice(String hashCurrentInvoice);

    /** Registros pendientes de envío para el scheduler de reintentos. */
    @EntityGraph(attributePaths = { "sale" })
    @Query("SELECT i FROM Invoice i WHERE i.aeatStatus = :pending " +
           "OR (i.aeatStatus = :rejected AND i.aeatRejectionReason = com.proconsi.electrobazar.model.AeatRejectionReason.NETWORK_ERROR)")
    List<Invoice> findPendingAndRejected(@Param("pending") AeatStatus pending,
                                         @Param("rejected") AeatStatus rejected);

    default List<Invoice> findPendingSend() {
        return findPendingAndRejected(AeatStatus.PENDING_SEND, AeatStatus.REJECTED);
    }
}
