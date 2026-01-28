package com.docencia.aed.controller;

import com.docencia.aed.entity.Event;
import com.docencia.aed.service.EventService;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
public class PublicEventController {

    private final EventService service;

    public PublicEventController(EventService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List approved events", description = "Returns all approved events for the public")
    public ResponseEntity<List<Event>> listApproved() {
        return ResponseEntity.ok(service.listPublicApproved());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get approved event by ID", description = "Returns a specific approved event by ID")
    public ResponseEntity<Event> getApproved(@PathVariable Long id) {
        return ResponseEntity.ok(service.getPublicApprovedById(id));
    }
}
