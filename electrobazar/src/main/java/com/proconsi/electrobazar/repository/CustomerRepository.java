package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByActiveTrueOrderByNameAsc();
    Optional<Customer> findByIdAndActiveTrue(Long id);
    Optional<Customer> findByTaxId(String taxId);
}
