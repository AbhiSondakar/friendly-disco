package com.ecoloop.identity;

import com.ecoloop.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    public record RoleUpdate(String role) {}

    private final UserRepository users;
    private final IdentityService identity;
    private final SessionRevocationService sessionRevocationService;

    public AdminUserController(UserRepository users,
                               IdentityService identity,
                               SessionRevocationService sessionRevocationService) {
        this.users = users;
        this.identity = identity;
        this.sessionRevocationService = sessionRevocationService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public PageResponse<IdentityService.UserDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {

        Boolean active = null;
        if (status != null) {
            switch (status) {
                case "active" -> active = true;
                case "suspended" -> active = false;
                default -> active = null;
            }
        }

        Specification<User> spec = Specification.where(UserSpecifications.withRole(role))
                .and(UserSpecifications.withActive(active))
                .and(UserSpecifications.withSearch(search));

        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        return PageResponse.of(users.findAll(spec, pageable)).mapContent(identity::toDto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/deactivate")
    public IdentityService.UserDto deactivate(@PathVariable UUID id) {
        return change(id, false);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/activate")
    public IdentityService.UserDto activate(@PathVariable UUID id) {
        return change(id, true);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/role")
    public IdentityService.UserDto changeRole(@PathVariable UUID id, @RequestBody RoleUpdate body) {
        var user = users.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (body == null || body.role() == null || body.role().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required");
        }
        try {
            user.setRole(User.Role.valueOf(body.role().trim().toUpperCase()).name());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role");
        }
        User saved = users.save(user);
        sessionRevocationService.revokeAllUserSessions(saved.getEmail());
        return identity.toDto(saved);
    }

    private IdentityService.UserDto change(UUID id, boolean active) {
        var user = users.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setActive(active);
        User saved = users.save(user);
        sessionRevocationService.revokeAllUserSessions(saved.getEmail());
        return identity.toDto(saved);
    }
}
