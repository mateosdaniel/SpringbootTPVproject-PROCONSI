package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.dto.AbonoRequest;
import com.proconsi.electrobazar.model.Abono;
import com.proconsi.electrobazar.service.AbonoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<Abono>> listarPorCliente(@PathVariable Long clienteId) {
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
