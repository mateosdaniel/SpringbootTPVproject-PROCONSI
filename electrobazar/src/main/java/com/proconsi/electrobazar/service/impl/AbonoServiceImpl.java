package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.dto.AbonoRequest;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.repository.AbonoRepository;
import com.proconsi.electrobazar.repository.CustomerRepository;
import com.proconsi.electrobazar.repository.SaleRepository;
import com.proconsi.electrobazar.service.AbonoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class AbonoServiceImpl implements AbonoService {

    private final AbonoRepository abonoRepository;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;

    @Override
    @Transactional
    public Abono createAbono(AbonoRequest request) {
        String cliInput = request.getClienteId();
        if (cliInput == null || cliInput.trim().isEmpty()) {
            throw new IllegalArgumentException("El cliente es obligatorio");
        }
        cliInput = cliInput.trim();

        Customer cliente = null;
        try {
            Long id = Long.parseLong(cliInput);
            cliente = customerRepository.findById(id).orElse(null);
        } catch (NumberFormatException e) {
            // Not a number, try Document/TaxId
        }

        if (cliente == null) {
            cliente = customerRepository.findByIdDocumentNumber(cliInput).orElse(null);
        }
        if (cliente == null) {
            cliente = customerRepository.findByTaxId(cliInput).orElse(null);
        }

        if (cliente == null) {
            throw new IllegalArgumentException("Cliente no encontrado con ese identificador o documento");
        }

        Sale venta = null;
        if (request.getVentaOriginalId() != null) {
            venta = saleRepository.findById(request.getVentaOriginalId())
                    .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));
        }

        // Si el tipo es DEVOLUCION, valide que el importe no supere el total de la venta original
        if (request.getTipoAbono() == TipoAbono.DEVOLUCION) {
            if (venta == null) {
                throw new IllegalArgumentException("El tipo DEVOLUCION requiere una venta original");
            }
            if (request.getImporte().compareTo(venta.getTotalAmount()) > 0) {
                throw new IllegalArgumentException("El importe del abono no puede superar el total de la venta original");
            }
        }

        // Guarde el importe siempre en negativo internamente
        BigDecimal importeNegativo = request.getImporte().abs().negate();

        Abono abono = Abono.builder()
                .ventaOriginal(venta)
                .cliente(cliente)
                .importe(importeNegativo)
                .fecha(LocalDateTime.now())
                .metodoPago(request.getMetodoPago())
                .tipoAbono(request.getTipoAbono())
                .motivo(request.getMotivo())
                .estado(EstadoAbono.PENDIENTE)
                .build();
                
        // Si el tipo es CREDITO_FAVOR, no descuente caja sino que quede como saldo pendiente del cliente
        // Esta lógica de caja se maneja al confirmar (APLICADO), por ahora se queda PENDIENTE y guardado sin afectar la caja.
        
        return abonoRepository.save(abono);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Abono> getAbonosByCliente(String clienteIdOrDoc) {
        if (clienteIdOrDoc == null || clienteIdOrDoc.trim().isEmpty()) {
            return List.of();
        }
        String doc = clienteIdOrDoc.trim();
        Customer cliente = null;
        try {
            Long id = Long.parseLong(doc);
            cliente = customerRepository.findById(id).orElse(null);
        } catch (NumberFormatException e) {
            // Ignore
        }
        if (cliente == null) {
            cliente = customerRepository.findByIdDocumentNumber(doc).orElse(null);
        }
        if (cliente == null) {
            cliente = customerRepository.findByTaxId(doc).orElse(null);
        }
        if (cliente == null) {
            return List.of();
        }
        return abonoRepository.findByClienteId(cliente.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Abono> getAbonosPaged(String clienteIdOrDoc, Pageable pageable) {
        if (clienteIdOrDoc == null || clienteIdOrDoc.trim().isEmpty()) {
            return abonoRepository.findAll(pageable);
        }
        String doc = clienteIdOrDoc.trim();
        Customer cliente = null;
        try {
            Long id = Long.parseLong(doc);
            cliente = customerRepository.findById(id).orElse(null);
        } catch (NumberFormatException e) {
            // not a number
        }
        if (cliente == null) cliente = customerRepository.findByIdDocumentNumber(doc).orElse(null);
        if (cliente == null) cliente = customerRepository.findByTaxId(doc).orElse(null);
        if (cliente == null) return Page.empty(pageable);
        return abonoRepository.findByClienteId(cliente.getId(), pageable);
    }


    @Override
    @Transactional
    public void anularAbono(Long id) {
        Abono abono = abonoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Abono no encontrado"));

        if (abono.getEstado() != EstadoAbono.PENDIENTE) {
            throw new IllegalStateException("Solo se pueden anular abonos en estado PENDIENTE");
        }

        abono.setEstado(EstadoAbono.ANULADO);
        abonoRepository.save(abono);
    }
}
