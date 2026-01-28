package com.docencia.aed.service.impl;

import com.docencia.aed.domain.EventCreateRequest;
import com.docencia.aed.domain.EventPatchRequest;
import com.docencia.aed.entity.Event;
import com.docencia.aed.entity.EventStatus;
import com.docencia.aed.exception.BadRequestException;
import com.docencia.aed.exception.ConflictException;
import com.docencia.aed.exception.ForbiddenException;
import com.docencia.aed.exception.ResourceNotFoundException;
import com.docencia.aed.infrastructure.security.AppSecurityProperties;
import com.docencia.aed.infrastructure.security.AppSecurityProperties.RolePermissions;
import com.docencia.aed.repository.EventRepository;
import com.docencia.aed.service.EventService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class EventServiceImpl implements EventService {

    private final EventRepository repo;
    private final AppSecurityProperties securityProps;

    public EventServiceImpl(EventRepository repo, AppSecurityProperties securityProps) {
        this.repo = repo;
        this.securityProps = securityProps;
    }

    @Override
    public List<Event> listPublicApproved() {
        return repo.findByStatus(EventStatus.APPROVED);
    }

    @Override
    public Event getPublicApprovedById(Long id) {
        Event event = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));
        if (event.getStatus() != EventStatus.APPROVED) {
            throw new IllegalArgumentException("Event not approved");
        }
        return event;
    }

    @Override
    public List<Event> listV2(String requestingUser, boolean isAdmin, EventStatus statusFilterOrNull) {
        if (isAdmin) {
            return repo.findByStatus(statusFilterOrNull);
        }
        return repo.findByCreatedByAndStatus(requestingUser, statusFilterOrNull);
    }

    @Override
    public Event getV2ById(String requestingUser, boolean isAdmin, Long id) {

        Event event = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));
        if (isAdmin) {
            return event;
        }
        if (!event.getCreatedBy().equals(requestingUser)) {
            throw new IllegalArgumentException("You do not have permission to access this event");
        }
        return event;
    }

    @Override
    public Event create(String requestingUser, boolean isAdmin, EventCreateRequest req) {
        RolePermissions permissions = isAdmin ? securityProps.getPermissions().getAdmin()
                : securityProps.getPermissions().getCollaborator();
        if (!permissions.isCanCreate()) {
            throw new ForbiddenException("You do not have permission to create events");
        }
        validEventData(req.getTitle(), req.getStartAt(), req.getEndAt());

        Event event = new Event();
        event.setTitle(req.getTitle());
        event.setDescription(req.getDescription());
        event.setStartAt(req.getStartAt());
        event.setEndAt(req.getEndAt());
        event.setCreatedBy(requestingUser);
        event.setStatus(EventStatus.DRAFT);
        event.setCreatedAt(Instant.now());

        return repo.save(event);
    }

    @Override
    public Event patch(String requestingUser, boolean isAdmin, Long id, EventPatchRequest req) {
        Event evento = getV2ById(requestingUser, isAdmin, id);
        if (!canEdit(evento, requestingUser, isAdmin)) {
            throw new ForbiddenException("You do not have permission to edit this event");
        }
        if (req.getTitle() != null && !req.getTitle().isBlank()) {
            evento.setTitle(req.getTitle());
        }
        if (req.getDescription() != null) {
            evento.setDescription(req.getDescription());
        }
        if (req.getStartAt() != null && req.getEndAt() != null) {
            if (req.getStartAt().isAfter(req.getEndAt())) {
                throw new BadRequestException("The start date cannot be later than the end date");
            }
            evento.setStartAt(req.getStartAt());
            evento.setEndAt(req.getEndAt());
        }
        return repo.save(evento);
    }

    @Override
    public Event submitForApproval(String requestingUser, boolean isAdmin, Long id) {
        Event evento = getV2ById(requestingUser, isAdmin, id);

        if (!canSubmit(evento, requestingUser, isAdmin)) {
            throw new ForbiddenException("You do not have permission to submit this event for approval");
        }
        validTansitionalStatus(evento, EventStatus.DRAFT, EventStatus.REJECTED, "submit");
        evento.setStatus(EventStatus.PENDING_APPROVAL);
        return repo.save(evento);
    }

    @Override
    public Event approve(String requestingUser, boolean isAdmin, Long id) {
        Event evento = getV2ById(requestingUser, isAdmin, id);
        RolePermissions permissions = efectivePermissions(isAdmin);
        if (!permissions.isCanApprove()) {
            throw new ForbiddenException("You do not have permission to approve events");
        }
        validTansitionalStatus(evento, EventStatus.PENDING_APPROVAL, null, "approve");
        evento.setStatus(EventStatus.APPROVED);
        return repo.save(evento);
    }

    @Override
    public Event reject(String requestingUser, boolean isAdmin, Long id, String reason) {
        Event evento = getV2ById(requestingUser, isAdmin, id);
        RolePermissions permissions = efectivePermissions(isAdmin);
        if (!permissions.isCanReject()) {
            throw new ForbiddenException("You do not have permission to decline events");
        }
        validTansitionalStatus(evento, EventStatus.PENDING_APPROVAL, null, "reject");

        evento.setStatus(EventStatus.REJECTED);
        evento.setRejectionReason(reason);
        return repo.save(evento);
    }

    @Override
    public void delete(String requestingUser, boolean isAdmin, Long id) {
        if (repo.findById(id).isEmpty()) {
            throw new ResourceNotFoundException("The event with id: " + id + " was not found");
        }

        RolePermissions permissions = efectivePermissions(isAdmin);
        if (!permissions.isCanDelete()) {
            throw new ForbiddenException("You do not have permission to delete events");
        }
        repo.deleteById(id);
    }

    private RolePermissions efectivePermissions(boolean isAdmin) {
        if (isAdmin) {
            return securityProps.getPermissions().getAdmin();
        }
        return securityProps.getPermissions().getCollaborator();
    }

    private boolean canEdit(Event event, String requestingUser, boolean isAdmin) {
        RolePermissions permissions = efectivePermissions(isAdmin);
        if (isAdmin) {
            return permissions.isCanEditAny();
        }
        return event.getCreatedBy().equals(requestingUser)
                && (event.getStatus() == EventStatus.DRAFT || event.getStatus() == EventStatus.REJECTED)
                && permissions.isCanEditOwnDraftOrRejected();
    }

    private void validTansitionalStatus(Event event, EventStatus requiredStatus, EventStatus alternativeStatus,
            String action) {
        EventStatus currentStatus = event.getStatus();
        boolean validStatus = false;

        if (currentStatus == requiredStatus) {
            validStatus = true;
        } else if (alternativeStatus != null && currentStatus == alternativeStatus) {
            validStatus = true;
        }

        if (!validStatus) {
            String message = "You can only " + action + " events that are in the following states: " + requiredStatus;
            if (alternativeStatus != null) {
                message += " or " + alternativeStatus;
            }
            throw new ConflictException(message);
        }
    }

    private void validEventData(String title, Instant startAt, Instant endAt) {
        if (title == null || title.isBlank()) {
            throw new BadRequestException("The title is required");
        }
        if (startAt != null && endAt != null && startAt.isAfter(endAt)) {
            throw new BadRequestException("The start date cannot be after the end date");
        }
    }

    private boolean canSubmit(Event event, String requestingUser, boolean isAdmin) {
        RolePermissions permissions = efectivePermissions(isAdmin);

        if (isAdmin) {
            return permissions.isCanSubmitForApproval()
                    && (event.getStatus() == EventStatus.DRAFT || event.getStatus() == EventStatus.REJECTED);
        }

        return event.getCreatedBy().equals(requestingUser)
                && (event.getStatus() == EventStatus.DRAFT || event.getStatus() == EventStatus.REJECTED)
                && permissions.isCanSubmitForApproval();
    }
}
