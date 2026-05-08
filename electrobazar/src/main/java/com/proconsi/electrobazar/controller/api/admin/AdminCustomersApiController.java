package com.proconsi.electrobazar.controller.api.admin;

import com.proconsi.electrobazar.dto.AdminCustomerListingDTO;
import com.proconsi.electrobazar.dto.AdminCustomerProjection;
import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST Controller for managing customers in the admin panel.
 */
@Slf4j
@RestController
@RequestMapping({ "/api/admin", "/admin/api" })
@RequiredArgsConstructor
public class AdminCustomersApiController {

    private final CustomerService customerService;

    @GetMapping("/customers")
    public ResponseEntity<Map<String, Object>> getCustomersPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String re,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Customer.CustomerType customerType = null;
        if (type != null && !type.isBlank()) {
            try {
                customerType = Customer.CustomerType.valueOf(type);
            } catch (Exception ignored) {}
        }
        Boolean hasRecargo = null;
        if ("yes".equalsIgnoreCase(re) || "true".equalsIgnoreCase(re))
            hasRecargo = true;
        else if ("no".equalsIgnoreCase(re) || "false".equalsIgnoreCase(re))
            hasRecargo = false;

        Set<String> allowedSort = Set.of("id", "name", "taxId", "city");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "name";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSort));
        org.springframework.data.domain.Slice<AdminCustomerProjection> sliceData = customerService.findAdminListing(search, customerType, hasRecargo, pageable);

        List<AdminCustomerListingDTO> list = sliceData.getContent().stream().map(c -> AdminCustomerListingDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .taxId(c.getTaxId())
                .email(c.getEmail())
                .phone(c.getPhone())
                .city(c.getCity())
                .type(c.getType())
                .hasRecargoEquivalencia(c.getHasRecargoEquivalencia() != null && c.getHasRecargoEquivalencia())
                .tariffId(c.getTariffId())
                .tariffName(c.getTariffName())
                .tariffColor(c.getTariffColor())
                .idDocumentType(c.getIdDocumentType())
                .idDocumentNumber(c.getIdDocumentNumber())
                .active(c.getActive() != null ? c.getActive() : true)
                .build()).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("number", sliceData.getNumber());
        response.put("hasNext", sliceData.hasNext());
        response.put("first", sliceData.isFirst());
        response.put("last", !sliceData.hasNext());
        return ResponseEntity.ok(response);
    }
}
