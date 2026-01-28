package com.docencia.aed.controller;

import com.docencia.aed.domain.EventCreateRequest;
import com.docencia.aed.domain.EventPatchRequest;
import com.docencia.aed.domain.RejectRequest;
import com.docencia.aed.entity.Event;
import com.docencia.aed.entity.EventStatus;
import com.docencia.aed.service.EventService;
import com.docencia.aed.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/events")
@Tag(name = "Events V2", description = "Complete event management for users and administrators")
public class EventControllerV2 {

    private final EventService service;

    public EventControllerV2(EventService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List events", description = "Lists all events, optionally filtering by status")
    public ResponseEntity<List<Event>> list(@RequestParam(value = "status", required = false) EventStatus status) {
        return ResponseEntity.ok(service.listV2(SecurityUtils.username(), SecurityUtils.isAdmin(), status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get event by ID", description = "Get a specific event by its ID")
    public ResponseEntity<Event> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getV2ById(SecurityUtils.username(), SecurityUtils.isAdmin(), id));
    }

    @PostMapping
    @Operation(summary = "Create event", description = "Create a new event")
    public ResponseEntity<Event> create(@Valid @RequestBody EventCreateRequest req) {
        return ResponseEntity.ok(service.create(SecurityUtils.username(), SecurityUtils.isAdmin(), req));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update event", description = "Partially update an existing event")
    public ResponseEntity<Event> patch(@PathVariable Long id, @RequestBody EventPatchRequest req) {
        return ResponseEntity.ok(service.patch(SecurityUtils.username(), SecurityUtils.isAdmin(), id, req));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit event for approval", description = "Marks an event as submitted for approval")
    public ResponseEntity<Event> submit(@PathVariable Long id) {
        return ResponseEntity.ok(service.submitForApproval(SecurityUtils.username(), SecurityUtils.isAdmin(), id));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve event", description = "Approve an event (admins only)")
    public ResponseEntity<Event> approve(@PathVariable Long id) {
        return ResponseEntity.ok(service.approve(SecurityUtils.username(), SecurityUtils.isAdmin(), id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject event", description = "Reject an event with a reason (admins only)")
    public ResponseEntity<Event> reject(@PathVariable Long id, @Valid @RequestBody RejectRequest req) {
        return ResponseEntity
                .ok(service.reject(SecurityUtils.username(), SecurityUtils.isAdmin(), id, req.getReason()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete event", description = "Delete an event by ID")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(SecurityUtils.username(), SecurityUtils.isAdmin(), id);
        return ResponseEntity.noContent().build();
    }
}
