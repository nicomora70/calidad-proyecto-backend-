package co.ritech.user;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.ritech.security.CurrentUser;
import co.ritech.user.UserDtos.ChangeRoleRequest;
import co.ritech.user.UserDtos.CreateUserRequest;
import co.ritech.user.UserDtos.UpdateUserRequest;
import co.ritech.user.UserDtos.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users (ADMIN)")
@SecurityRequirement(name = "bearer")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user with a temporary password (must be changed on first login)")
    public UserResponse create(
            @Parameter(hidden = true) CurrentUser actor, @Valid @RequestBody CreateUserRequest request) {
        return UserResponse.from(service.create(actor, request));
    }

    @GetMapping
    @Operation(summary = "List users, optionally filtered by role and status")
    public Page<UserResponse> list(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(role, active, pageable).map(UserResponse::from);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return UserResponse.from(service.get(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename and/or activate/deactivate a user")
    public UserResponse update(
            @Parameter(hidden = true) CurrentUser actor,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return UserResponse.from(service.update(actor, id, request));
    }

    @PatchMapping("/{id}/role")
    public UserResponse changeRole(
            @Parameter(hidden = true) CurrentUser actor,
            @PathVariable UUID id,
            @Valid @RequestBody ChangeRoleRequest request) {
        return UserResponse.from(service.changeRole(actor, id, request.role()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a user (their simulations are kept)")
    public void delete(@Parameter(hidden = true) CurrentUser actor, @PathVariable UUID id) {
        service.delete(actor, id);
    }
}
