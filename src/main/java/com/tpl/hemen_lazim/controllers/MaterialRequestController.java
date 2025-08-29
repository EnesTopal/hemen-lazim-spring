package com.tpl.hemen_lazim.controllers;

import com.tpl.hemen_lazim.model.ApiResponse;
import com.tpl.hemen_lazim.model.DTOs.MaterialRequestCreateDTO;
import com.tpl.hemen_lazim.model.DTOs.MaterialRequestDTO;
import com.tpl.hemen_lazim.model.enums.Category;
import com.tpl.hemen_lazim.model.enums.RequestStatus;
import com.tpl.hemen_lazim.security.SecurityUtils;
import com.tpl.hemen_lazim.services.MaterialRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/requests")
public class MaterialRequestController {

    private final MaterialRequestService service;

    public MaterialRequestController(MaterialRequestService service) {
        this.service = service;
    }

    // Create
    @PostMapping
    public ResponseEntity<ApiResponse<MaterialRequestDTO>> create(@Valid @RequestBody MaterialRequestCreateDTO body) {
        var me = SecurityUtils.currentUserId();
        var dto = service.create(me, body);
        return ResponseEntity.ok(new ApiResponse<>("Created", dto));
    }

    // Get by id
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MaterialRequestDTO>> get(@PathVariable UUID id) {
        var dto = service.get(id);
        return ResponseEntity.ok(new ApiResponse<>("OK", dto));
    }

    // My requests (optional status)
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<MaterialRequestDTO>>> mine(
            @RequestParam(required = false) RequestStatus status) {
        var me = SecurityUtils.currentUserId();
        var list = service.listMine(me, status);
        return ResponseEntity.ok(new ApiResponse<>("OK", list));
    }

    // Nearby (ANLIK): client konumu + radius
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<MaterialRequestDTO>>> nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) Integer radiusMeters,
            @RequestParam(required = false) Category category
    ) {
        var list = service.listNearby(lat, lng, radiusMeters, category);
        return ResponseEntity.ok(new ApiResponse<>("OK", list));
    }

    // Status ops: cancel
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID id) {
        var me = SecurityUtils.currentUserId();
        service.cancel(me, id);
        return ResponseEntity.ok(new ApiResponse<>("Cancelled"));
    }

    // Status ops: complete
    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<Void>> complete(@PathVariable UUID id) {
        var me = SecurityUtils.currentUserId();
        service.complete(me, id);
        return ResponseEntity.ok(new ApiResponse<>("Completed"));
    }

    // Delete (owner)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        var me = SecurityUtils.currentUserId();
        service.delete(me, id);
        return ResponseEntity.ok(new ApiResponse<>("Deleted"));
    }
}
