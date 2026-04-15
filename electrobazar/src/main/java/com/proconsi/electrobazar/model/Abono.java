package com.proconsi.electrobazar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "abonos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Abono {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_original_id")
    private Sale ventaOriginal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    @JsonIgnoreProperties({"tariff", "hasRecargoEquivalencia", "active", "address", "city", "postalCode", "type"})
    private Customer cliente;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal importe;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MetodoPagoAbono metodoPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoAbono tipoAbono;

    @Column(length = 255)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoAbono estado;
}
