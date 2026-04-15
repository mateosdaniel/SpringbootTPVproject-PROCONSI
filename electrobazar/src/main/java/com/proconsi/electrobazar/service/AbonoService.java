package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.dto.AbonoRequest;
import com.proconsi.electrobazar.model.Abono;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AbonoService {
    Abono createAbono(AbonoRequest request);
    List<Abono> getAbonosByCliente(String clienteIdOrDoc);
    Page<Abono> getAbonosPaged(String clienteIdOrDoc, Pageable pageable);
    void anularAbono(Long id);
}
