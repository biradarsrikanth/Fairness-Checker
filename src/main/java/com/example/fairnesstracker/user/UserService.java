package com.example.fairnesstracker.user;

import com.example.fairnesstracker.audit.AuditAction;
import com.example.fairnesstracker.audit.AuditService;
import com.example.fairnesstracker.auth.RefreshTokenService;
import com.example.fairnesstracker.common.exception.ConflictException;
import com.example.fairnesstracker.common.exception.ResourceNotFoundException;
import com.example.fairnesstracker.common.exception.ValidationException;
import com.example.fairnesstracker.common.web.PageResponse;
import com.example.fairnesstracker.common.web.Search;
import com.example.fairnesstracker.common.web.Sorting;
import com.example.fairnesstracker.engineer.Engineer;
import com.example.fairnesstracker.engineer.EngineerRepository;
import com.example.fairnesstracker.security.CurrentUser;
import com.example.fairnesstracker.team.Team;
import com.example.fairnesstracker.team.TeamRepository;
import com.example.fairnesstracker.user.dto.UserDtos.CreateUserRequest;
import com.example.fairnesstracker.user.dto.UserDtos.TemporaryPasswordResponse;
import com.example.fairnesstracker.user.dto.UserDtos.UpdateUserRequest;
import com.example.fairnesstracker.user.dto.UserDtos.UserAccountResponse;
import com.example.fairnesstracker.user.dto.UserDtos.UserFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Admins manage logins. There's no sign-up: users are created here with a one-time password they must
 * change. An admin can't lock themselves out, and the last enabled admin can't be removed, demoted or
 * disabled. Anything that changes what a user may do ends their sessions.
 */
@Slf4j
@Service
@Transactional
public class UserService {

    static final Set<String> SORTABLE = Set.of("email", "fullName", "role", "lastLoginAt");

    private final AppUserRepository users;
    private final EngineerRepository engineers;
    private final TeamRepository teams;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final AuditService audit;
    private final UserMapper mapper;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public UserService(AppUserRepository users, EngineerRepository engineers, TeamRepository teams,
                       PasswordEncoder passwordEncoder, RefreshTokenService refreshTokens, AuditService audit,
                       UserMapper mapper, Clock clock) {
        this.users = users;
        this.engineers = engineers;
        this.teams = teams;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserAccountResponse> list(UserFilter filter, Pageable pageable) {
        List<Specification<AppUser>> conditions = new ArrayList<>();
        if (filter.role() != null) {
            conditions.add((user, query, cb) -> cb.equal(user.get("role"), filter.role()));
        }
        if (filter.enabled() != null) {
            conditions.add((user, query, cb) -> cb.equal(user.get("enabled"), filter.enabled()));
        }
        // The user's % and _ are taken literally (they used to match anything)
        Search.term(filter.q()).ifPresent(term -> conditions.add((user, query, cb) ->
                Search.anyContains(cb, term, user.get("email"), user.get("fullName"))));
        return PageResponse.from(users.findAll(Specification.allOf(conditions), Sorting.restrict(pageable, SORTABLE))
                .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public UserAccountResponse get(long id) {
        return mapper.toResponse(find(id));
    }

    public TemporaryPasswordResponse create(CreateUserRequest request) {
        String email = request.email().strip();
        if (users.existsByEmail(email)) {
            throw ConflictException.onField("email", "A user with " + email + " already exists");
        }
        AppUser user = new AppUser(email, request.fullName().strip(), request.role());
        user.setEngineer(engineerFor(request.role(), request.engineerId(), null));
        user.setTeams(teamsFor(request.role(), request.teamIds()));
        String password = setTemporaryPassword(user);
        users.save(user);

        audit.record(AuditAction.USER_CREATED, "USER", user.getId(), details(
                "email", user.getEmail(), "role", user.getRole(), "engineerId", request.engineerId(),
                "teamIds", ids(user.getTeams())));
        return new TemporaryPasswordResponse(mapper.toResponse(user), password);
    }

    public UserAccountResponse update(long id, UpdateUserRequest request) {
        AppUser user = find(id);
        String email = request.email().strip();
        boolean demoting = user.getRole() == Role.ADMIN && request.role() != Role.ADMIN;
        boolean disabling = user.isEnabled() && !request.enabled();
        if (isCurrentUser(user) && (demoting || disabling)) {
            throw new ConflictException("You can't demote or disable your own account");
        }
        if ((demoting || disabling) && user.isEnabled() && isLastEnabledAdmin(user)) {
            throw new ConflictException("This is the last enabled admin; make someone else an admin first");
        }
        if (!email.equalsIgnoreCase(user.getEmail()) && users.existsByEmail(email)) {
            throw ConflictException.onField("email", "A user with " + email + " already exists");
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        if (!email.equals(user.getEmail())) changes.put("email", email);
        if (!request.fullName().strip().equals(user.getFullName())) changes.put("fullName", request.fullName().strip());
        if (request.role() != user.getRole()) changes.put("role", request.role());
        Long currentEngineerId = user.getEngineer() != null ? user.getEngineer().getId() : null;
        if (!Objects.equals(request.engineerId(), currentEngineerId)) changes.put("engineerId", request.engineerId());
        if (request.enabled() != user.isEnabled()) changes.put("enabled", request.enabled());

        boolean roleChanged = request.role() != user.getRole();
        user.setEngineer(engineerFor(request.role(), request.engineerId(), user));
        user.setEmail(email);
        user.setFullName(request.fullName().strip());
        user.setRole(request.role());
        user.setEnabled(request.enabled());
        if (request.role() != Role.MANAGER && !user.getTeams().isEmpty()) {
            changes.put("teamIds", List.of());
            user.getTeams().clear();
        }
        // The token's role claim and the user's sessions were issued for what they could do before
        if (roleChanged || disabling) {
            refreshTokens.revokeAll(user);
        }
        if (!changes.isEmpty()) {
            audit.record(AuditAction.USER_UPDATED, "USER", user.getId(), changes);
        }
        return mapper.toResponse(user);
    }

    public UserAccountResponse setTeams(long id, Set<Long> teamIds) {
        AppUser user = find(id);
        user.setTeams(teamsFor(user.getRole(), teamIds));
        audit.record(AuditAction.USER_TEAMS_CHANGED, "USER", user.getId(), Map.of("teamIds", ids(user.getTeams())));
        return mapper.toResponse(user);
    }

    /** A new one-time password; the user's sessions end and any lock is lifted. */
    public TemporaryPasswordResponse resetPassword(long id) {
        AppUser user = find(id);
        String password = setTemporaryPassword(user);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        int ended = refreshTokens.revokeAll(user);
        audit.record(AuditAction.USER_PASSWORD_RESET, "USER", user.getId(), Map.of("sessionsEnded", ended));
        return new TemporaryPasswordResponse(mapper.toResponse(user), password);
    }

    public UserAccountResponse unlock(long id) {
        AppUser user = find(id);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        audit.record(AuditAction.USER_UNLOCKED, "USER", user.getId(), Map.of());
        return mapper.toResponse(user);
    }

    public void delete(long id) {
        AppUser user = find(id);
        if (isCurrentUser(user)) {
            throw new ConflictException("You can't delete your own account");
        }
        if (user.isEnabled() && isLastEnabledAdmin(user)) {
            throw new ConflictException("This is the last enabled admin; make someone else an admin first");
        }
        // Refresh tokens and team access go with it (ON DELETE CASCADE)
        users.delete(user);
        audit.record(AuditAction.USER_DELETED, "USER", id, Map.of("email", user.getEmail(), "role", user.getRole()));
    }

    // -------------------------------------------------------------------------------------------------

    private AppUser find(long id) {
        return users.findWithAccessById(id).orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    }

    // ENGINEER logins need an engineer; any login may point at one, as long as no other login does
    private Engineer engineerFor(Role role, Long engineerId, AppUser self) {
        if (engineerId == null) {
            if (role == Role.ENGINEER) {
                throw ValidationException.onField("engineerId", "is required for an ENGINEER login");
            }
            return null;
        }
        Engineer engineer = engineers.findById(engineerId)
                .orElseThrow(() -> ValidationException.onField("engineerId", "Engineer " + engineerId + " doesn't exist"));
        users.findByEngineer_Id(engineerId)
                .filter(other -> self == null || !other.getId().equals(self.getId()))
                .ifPresent(other -> {
                    throw ConflictException.onField("engineerId",
                            "Engineer " + engineerId + " already has a login (" + other.getEmail() + ")");
                });
        return engineer;
    }

    private Set<Team> teamsFor(Role role, Set<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return new HashSet<>();
        }
        if (role != Role.MANAGER) {
            throw ValidationException.onField("teamIds", "only MANAGER logins have team access");
        }
        List<Team> found = teams.findAllById(teamIds);
        if (found.size() != teamIds.size()) {
            Set<Long> missing = new TreeSet<>(teamIds);
            found.forEach(team -> missing.remove(team.getId()));
            throw ValidationException.onField("teamIds", "Unknown teams: " + missing);
        }
        return new HashSet<>(found);
    }

    // 16 URL-safe characters (96 bits); the user replaces it at first sign-in
    private String setTemporaryPassword(AppUser user) {
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.setPassword(passwordEncoder.encode(password), clock.instant());
        user.setMustChangePassword(true);
        return password;
    }

    private boolean isLastEnabledAdmin(AppUser user) {
        return user.getRole() == Role.ADMIN && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1;
    }

    private static boolean isCurrentUser(AppUser user) {
        return CurrentUser.id().filter(id -> id.equals(user.getId())).isPresent();
    }

    private static List<Long> ids(Set<Team> teams) {
        return teams.stream().map(Team::getId).sorted().toList();
    }

    // Map.of refuses nulls; audit details may have them
    private static Map<String, Object> details(Object... keysAndValues) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            details.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return details;
    }
}
