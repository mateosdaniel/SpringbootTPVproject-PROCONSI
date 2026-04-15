package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.dto.AbonoRequest;
import com.proconsi.electrobazar.model.Abono;
import com.proconsi.electrobazar.service.AbonoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/abonos")
@RequiredArgsConstructor
public class AbonoController {

    private final AbonoService abonoService;

    @PostMapping
    public ResponseEntity<?> createAbono(@Valid @RequestBody AbonoRequest request) {
        try {
            Abono abono = abonoService.createAbono(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(abono);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al crear el abono");
        }
    }

    /**
     * Paginated list of all abonos, optionally filtered by customer ID/document.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listarAbonos(
            @RequestParam(required = false) String cliente,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "fecha") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<Abono> pageData = abonoService.getAbonosPaged(cliente, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", pageData.getContent());
        response.put("totalPages", pageData.getTotalPages());
        response.put("totalElements", pageData.getTotalElements());
        response.put("currentPage", pageData.getNumber());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<Abono>> listarPorCliente(@PathVariable String clienteId) {
        return ResponseEntity.ok(abonoService.getAbonosByCliente(clienteId));
    }

    @PatchMapping("/{id}/anular")
    public ResponseEntity<?> anularAbono(@PathVariable Long id) {
        try {
            abonoService.anularAbono(id);
            return ResponseEntity.ok("Abono anulado con éxito");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al anular el abono");
        }
    }
}
