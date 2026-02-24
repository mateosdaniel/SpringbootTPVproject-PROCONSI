package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Customer;
import java.util.List;

public interface CustomerService {
    List<Customer> findAll();
    List<Customer> findAllActive();
    Customer findById(Long id);
    Customer save(Customer customer);
    Customer update(Long id, Customer customer);
    void delete(Long id);
}
