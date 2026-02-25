package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.repository.CashRegisterRepository;
import com.proconsi.electrobazar.repository.SaleRepository;
import com.proconsi.electrobazar.service.CashRegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CashRegisterServiceImpl implements CashRegisterService {

        private final CashRegisterRepository cashRegisterRepository;
        private final SaleRepository saleRepository;

        @Override
        @Transactional(readOnly = true)
        public CashRegister findById(Long id) {
                return cashRegisterRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Cierre de caja no encontrado con id: " + id));
        }

        @Override
        @Transactional(readOnly = true)
        public List<CashRegister> findAllClosed() {
                return cashRegisterRepository.findByClosedTrueOrderByRegisterDateDesc();
        }

        @Override
        @Transactional(readOnly = true)
        public CashRegister findTodayIfClosed() {
                return cashRegisterRepository.findByRegisterDateAndClosedTrue(LocalDate.now())
                                .orElseThrow(() -> new ResourceNotFoundException("No hay cierre de caja para hoy"));
        }

        @Override
        public CashRegister closeCashRegister(BigDecimal closingBalance, String notes) {
                LocalDate today = LocalDate.now();

                // Obtener registro abierto actual
                CashRegister register = cashRegisterRepository.findFirstByClosedFalseOrderByRegisterDateDesc()
                                .orElse(CashRegister.builder()
                                                .registerDate(today)
                                                .openingBalance(BigDecimal.ZERO)
                                                .build());

                // Calcular totales solo desde la apertura de este registro
                LocalDateTime startTime = register.getOpeningTime() != null ? register.getOpeningTime()
                                : today.atStartOfDay();
                LocalDateTime endOfDay = today.plusDays(1).atStartOfDay().minusNanos(1);

                BigDecimal cashSales = saleRepository
                                .sumTotalBetweenByPaymentMethod(startTime, endOfDay, PaymentMethod.CASH)
                                .orElse(BigDecimal.ZERO);
                BigDecimal cardSales = saleRepository
                                .sumTotalBetweenByPaymentMethod(startTime, endOfDay, PaymentMethod.CARD)
                                .orElse(BigDecimal.ZERO);
                BigDecimal totalSales = cashSales.add(cardSales);

                register.setCashSales(cashSales);
                register.setCardSales(cardSales);
                register.setTotalSales(totalSales);
                register.setClosingBalance(closingBalance);
                register.setDifference(closingBalance.subtract(register.getOpeningBalance().add(totalSales)));
                register.setNotes(notes);
                register.setClosedAt(LocalDateTime.now());
                register.setClosed(true);

                CashRegister closedRegister = cashRegisterRepository.save(register);

                // Crear automáticamente un nuevo registro abierto para poder continuar
                // vendiendo
                CashRegister newRegister = CashRegister.builder()
                                .registerDate(today)
                                .openingBalance(closingBalance)
                                .openingTime(LocalDateTime.now())
                                .cashSales(BigDecimal.ZERO)
                                .cardSales(BigDecimal.ZERO)
                                .totalSales(BigDecimal.ZERO)
                                .closed(false)
                                .build();
                cashRegisterRepository.save(newRegister);

                return closedRegister;
        }

        @Override
        public CashRegister getTodayRegister() {
                LocalDate today = LocalDate.now();

                // Buscar registro abierto de hoy
                return cashRegisterRepository.findFirstByClosedFalseOrderByRegisterDateDesc()
                                .orElseGet(() -> {
                                        // Si no existe, crear uno nuevo para hoy
                                        CashRegister newRegister = CashRegister.builder()
                                                        .registerDate(today)
                                                        .openingBalance(BigDecimal.ZERO)
                                                        .openingTime(LocalDateTime.now())
                                                        .cashSales(BigDecimal.ZERO)
                                                        .cardSales(BigDecimal.ZERO)
                                                        .totalSales(BigDecimal.ZERO)
                                                        .closed(false)
                                                        .build();
                                        return cashRegisterRepository.save(newRegister);
                                });
        }
}
