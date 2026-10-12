package com.example.fairnesstracker.user;

import com.example.fairnesstracker.common.web.PageResponse;
import com.example.fairnesstracker.user.dto.UserDtos.CreateUserRequest;
import com.example.fairnesstracker.user.dto.UserDtos.TeamAccessRequest;
import com.example.fairnesstracker.user.dto.UserDtos.TemporaryPasswordResponse;
import com.example.fairnesstracker.user.dto.UserDtos.UpdateUserRequest;
import com.example.fairnesstracker.user.dto.UserDtos.UserAccountResponse;
import com.example.fairnesstracker.user.dto.UserDtos.UserFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "Logins and what they may see (admins only)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "List users", description = "Sort by email (default), fullName, role or lastLoginAt.")
    @GetMapping
    public PageResponse<UserAccountResponse> list(@Valid @ParameterObject UserFilter filter,
                                                  @ParameterObject @SortDefault("email") Pageable pageable) {
        return userService.list(filter, pageable);
    }

    @GetMapping("/{id}")
    public UserAccountResponse get(@PathVariable long id) {
        return userService.get(id);
    }

    @Operation(summary = "Create a user",
            description = "Returns a one-time password, shown only here; the user must change it at first sign-in.")
    @PostMapping
    public ResponseEntity<TemporaryPasswordResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(userService.create(request));
    }

    @Operation(summary = "Update a user", description = "Changing the role or disabling the user ends their sessions.")
    @PutMapping("/{id}")
    public UserAccountResponse update(@PathVariable long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @Operation(summary = "Set the teams a manager can see", description = "Replaces the list; only for MANAGER logins.")
    @PutMapping("/{id}/teams")
    public UserAccountResponse setTeams(@PathVariable long id, @Valid @RequestBody TeamAccessRequest request) {
        return userService.setTeams(id, request.teamIds());
    }

    @Operation(summary = "Reset a user's password",
            description = "Returns a new one-time password, ends the user's sessions and lifts any lock.")
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<TemporaryPasswordResponse> resetPassword(@PathVariable long id) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(userService.resetPassword(id));
    }

    @Operation(summary = "Lift a lock from failed sign-ins")
    @PostMapping("/{id}/unlock")
    public UserAccountResponse unlock(@PathVariable long id) {
        return userService.unlock(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
