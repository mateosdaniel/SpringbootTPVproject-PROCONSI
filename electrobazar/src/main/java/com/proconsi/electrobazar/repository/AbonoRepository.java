package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Abono;
import com.proconsi.electrobazar.model.TipoAbono;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AbonoRepository extends JpaRepository<Abono, Long> {
    List<Abono> findByClienteId(Long clienteId);
    List<Abono> findByTipoAbono(TipoAbono tipoAbono);
}
