package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.CashRegister;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashRegisterRepository extends JpaRepository<CashRegister, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "worker" })
    Optional<CashRegister> findByRegisterDateAndClosedTrue(LocalDate registerDate);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "worker" })
    List<CashRegister> findByClosedTrueOrderByRegisterDateDesc();

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "worker" })
    Optional<CashRegister> findFirstByClosedFalseOrderByRegisterDateDesc();
}
