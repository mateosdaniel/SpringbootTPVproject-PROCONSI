package com.proconsi.electrobazar.controller.api.admin;

import com.proconsi.electrobazar.dto.AdminRoleListingDTO;
import com.proconsi.electrobazar.dto.AdminWorkerListingDTO;
import com.proconsi.electrobazar.dto.AdminWorkerProjection;
import com.proconsi.electrobazar.model.Role;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.model.WorkerRepository;
import com.proconsi.electrobazar.security.JwtService;
import com.proconsi.electrobazar.service.AdminPinService;
import com.proconsi.electrobazar.service.RoleService;
import com.proconsi.electrobazar.service.WorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST Controller for managing workers, roles, and administrative PINs.
 */
@Slf4j
@RestController
@RequestMapping({ "/api/admin", "/admin/api" })
@RequiredArgsConstructor
public class AdminWorkersApiController {

    private final WorkerService workerService;
    private final RoleService roleService;
    private final AdminPinService adminPinService;
    private final JwtService jwtService;
    private final WorkerRepository workerRepository;


    @GetMapping("/workers")
    public ResponseEntity<Map<String, Object>> getWorkersPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "username") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Set<String> allowedSort = Set.of("id", "username", "active");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "username";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSort));
        org.springframework.data.domain.Slice<AdminWorkerProjection> workersSlice = workerService.findAdminListing(search, roleId, active, pageable);

        List<AdminWorkerListingDTO> list = workersSlice.getContent().stream().map(w -> AdminWorkerListingDTO.builder()
                .id(w.getId())
                .username(w.getUsername())
                .active(w.isActive())
                .roleId(w.getRoleId())
                .roleName(w.getRoleName())
                .hasSales(w.getHasSales())
                .build()).collect(java.util.stream.Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("number", workersSlice.getNumber());
        response.put("hasNext", workersSlice.hasNext());
        response.put("first", workersSlice.isFirst());
        response.put("last", !workersSlice.hasNext());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/roles")
    public ResponseEntity<Map<String, Object>> getRolesPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> permissions,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Set<String> allowedSort = Set.of("id", "name");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "name";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSort));
        org.springframework.data.domain.Slice<Role> sliceData = roleService.getFilteredRoles(search, permissions, pageable);

        List<AdminRoleListingDTO> list = sliceData.getContent().stream()
                .filter(r -> !"ADMIN".equalsIgnoreCase(r.getName()))
                .map(r -> {
                    long count = workerRepository.countByRole_Id(r.getId());
                    Set<String> perms = new HashSet<>(r.getPermissions());
                    return AdminRoleListingDTO.builder()
                            .id(r.getId())
                            .name(r.getName())
                            .description(r.getDescription())
                            .permissions(perms)
                            .workerCount(count)
                            .build();
                }).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("number", sliceData.getNumber());
        response.put("hasNext", sliceData.hasNext());
        response.put("first", sliceData.isFirst());
        response.put("last", !sliceData.hasNext());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/roles/{id}/workers")
    public ResponseEntity<?> getWorkersByRole(@PathVariable Long id) {
        List<Map<String, Object>> workers = workerRepository.findByRole_Id(id).stream()
                .map(w -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", w.getId());
                    m.put("username", w.getUsername());
                    m.put("active", w.isActive());
                    return m;
                }).toList();
        return ResponseEntity.ok(workers);
    }

    @DeleteMapping("/workers/{id}")
    public ResponseEntity<?> deleteWorker(@PathVariable Long id) {
        workerService.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-pin")
    public ResponseEntity<?> verifyPin(@RequestBody Map<String, String> body) {
        String pin = body.get("pin");
        if (adminPinService.verifyPin(pin)) {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String username = auth.getName();
                Optional<Worker> workerOpt = workerService.findByUsername(username);

                if (workerOpt.isPresent()) {
                    Worker worker = workerOpt.get();
                    Set<String> permissions = worker.getEffectivePermissions();
                    permissions.add("ACCESO_TOTAL_ADMIN");

                    String newToken = jwtService.generateToken(worker.getUsername(), worker.getId(), permissions);
                    return ResponseEntity.ok(Map.of(
                            "ok", true,
                            "token", newToken,
                            "worker", worker));
                }
            }
            return ResponseEntity.ok(Map.of("ok", true));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "PIN incorrecto"));
        }
    }

    @PostMapping("/update-pin")
    public ResponseEntity<?> updatePin(@RequestBody Map<String, String> body) {
        try {
            adminPinService.updatePin(body.get("currentPin"), body.get("newPin"));
            return ResponseEntity.ok(Map.of("message", "PIN de administrador actualizado correctamente."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
