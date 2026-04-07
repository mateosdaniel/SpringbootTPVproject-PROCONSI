package com.proconsi.electrobazar.dto;

import com.proconsi.electrobazar.model.MetodoPagoAbono;
import com.proconsi.electrobazar.model.TipoAbono;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AbonoRequest {
    
    private Long ventaOriginalId;

    @NotNull(message = "El cliente es obligatorio")
    private Long clienteId;

    @NotNull(message = "El importe es obligatorio")
    @Positive(message = "El importe debe ser un valor positivo")
    private BigDecimal importe;

    @NotNull(message = "El método de pago es obligatorio")
    private MetodoPagoAbono metodoPago;

    @NotNull(message = "El tipo de abono es obligatorio")
    private TipoAbono tipoAbono;

    private String motivo;
}
