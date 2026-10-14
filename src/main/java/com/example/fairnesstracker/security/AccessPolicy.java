package com.example.fairnesstracker.security;

import com.example.fairnesstracker.alert.AlertRepository;
import com.example.fairnesstracker.common.exception.ForbiddenException;
import com.example.fairnesstracker.common.exception.ValidationException;
import com.example.fairnesstracker.engineer.EngineerRepository;
import com.example.fairnesstracker.scoring.dto.ScoreQuery;
import com.example.fairnesstracker.team.Team;
import com.example.fairnesstracker.team.TeamRepository;
import com.example.fairnesstracker.user.AppUser;
import com.example.fairnesstracker.user.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.stream.Collectors;

/**
 * Which teams and engineers the signed-in user may see and change. Roles alone are checked with
 * {@code @PreAuthorize}; this adds the data each role is limited to. The user's teams are read from the
 * database once per request (not from the token), so a change of access applies immediately.
 *
 * <p>Denials are 403s. Checks on something that doesn't exist pass, so the service answers 404 as before.
 */
@Component
public class AccessPolicy {

    private static final String SCOPE_ATTRIBUTE = AccessPolicy.class.getName() + ".scope";

    private final AppUserRepository users;
    private final EngineerRepository engineers;
    private final TeamRepository teams;
    private final AlertRepository alerts;

    public AccessPolicy(AppUserRepository users, EngineerRepository engineers, TeamRepository teams,
                        AlertRepository alerts) {
        this.users = users;
        this.engineers = engineers;
        this.teams = teams;
        this.alerts = alerts;
    }

    /** What the current user may see, for limiting lists. */
    @Transactional(readOnly = true)
    public DataScope scope() {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null && request.getAttribute(SCOPE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof DataScope cached) {
            return cached;
        }
        DataScope scope = resolveScope();
        if (request != null) {
            request.setAttribute(SCOPE_ATTRIBUTE, scope, RequestAttributes.SCOPE_REQUEST);
        }
        return scope;
    }

    // --- Reading -------------------------------------------------------------------------------------

    public void requireTeam(long teamId) {
        if (!scope().canSeeTeam(teamId)) {
            throw new ForbiddenException("You don't have access to team " + teamId);
        }
    }

    public void requireEngineer(long engineerId) {
        DataScope scope = scope();
        if (scope.isAll()) {
            return;
        }
        engineers.findTeamIdById(engineerId).ifPresent(teamId -> {
            if (!scope.canSeeEngineer(engineerId, teamId)) {
                throw new ForbiddenException("You don't have access to engineer " + engineerId);
            }
        });
    }

    @Transactional(readOnly = true)
    public void requireAlert(long alertId) {
        DataScope scope = scope();
        if (scope.isAll()) {
            return;
        }
        alerts.findById(alertId).ifPresent(alert -> {
            if (alert.getEngineer() == null || !scope.canSeeEngineer(alert.getEngineer().getId(),
                    alert.getEngineer().getTeam().getId())) {
                throw new ForbiddenException("You don't have access to alert " + alertId);
            }
        });
    }

    // --- Changing ------------------------------------------------------------------------------------
    // Engineers can't change anything (@PreAuthorize); managers only within their teams

    public void requireManagedTeam(long teamId) {
        DataScope scope = scope();
        if (scope.engineerId() != null || !scope.canSeeTeam(teamId)) {
            throw new ForbiddenException("You can only change your own teams");
        }
    }

    /** As {@link #requireManagedTeam}, by name; unknown names pass so the service can report them. */
    public void requireManagedTeamNamed(String teamName) {
        if (teamName != null) {
            teams.findByName(teamName.strip()).ifPresent(team -> requireManagedTeam(team.getId()));
        }
    }

    public void requireManagedEngineer(Long engineerId) {
        if (engineerId != null) {
            engineers.findTeamIdById(engineerId).ifPresent(this::requireManagedTeam);
        }
    }

    // --- Scores --------------------------------------------------------------------------------------

    /**
     * The team a team-level score is computed for. Admins may leave it empty (all teams). Others get their
     * own team when there's only one, must name one they can see otherwise, and an engineer may only ask for
     * their own team, and only when {@code engineersAllowed}.
     */
    @Transactional(readOnly = true)
    public ScoreQuery teamScoped(ScoreQuery query, boolean engineersAllowed) {
        DataScope scope = scope();
        if (scope.isAll()) {
            return query;
        }
        if (scope.engineerId() != null && !engineersAllowed) {
            throw new ForbiddenException("Engineers can see their own scores only: /api/v1/scores/engineers/"
                    + scope.engineerId());
        }
        String requested = query.team() == null || query.team().isBlank() ? null : query.team().strip();
        if (requested == null) {
            if (scope.teamIds().size() != 1) {
                throw ValidationException.onField("team", "is required: you can see several teams");
            }
            Team only = teams.findById(scope.teamIds().iterator().next()).orElseThrow();
            return new ScoreQuery(query.days(), query.start(), query.end(), only.getName());
        }
        Team team = teams.findByName(requested)
                .orElseThrow(() -> ValidationException.onField("team", "Unknown team '" + requested + "'"));
        requireTeam(team.getId());
        return query;
    }

    /** The engineer the current user is, if they're an ENGINEER: score reports then show only their row. */
    public Long ownEngineerOnly() {
        return scope().engineerId();
    }

    // -------------------------------------------------------------------------------------------------

    private DataScope resolveScope() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            AppUser user = users.findWithAccessById(Long.parseLong(jwt.getName()))
                    .orElseThrow(() -> new InactiveAccountException("This account no longer exists; sign in again"));
            if (!user.isEnabled()) {
                throw new InactiveAccountException("This account has been disabled");
            }
            // The role in the database wins over the token's: a demotion applies at once
            return switch (user.getRole()) {
                case ADMIN -> DataScope.ALL;
                case MANAGER -> DataScope.teams(user.getTeams().stream().map(Team::getId).collect(Collectors.toSet()));
                case ENGINEER -> DataScope.engineer(user.getEngineer().getId(), user.getEngineer().getTeam().getId());
            };
        }
        // Not a token (e.g. @WithMockUser in tests): only an admin authority grants anything
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return DataScope.ALL;
        }
        throw new ForbiddenException();
    }
}
