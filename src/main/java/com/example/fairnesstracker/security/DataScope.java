package com.example.fairnesstracker.security;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;
import java.util.function.Function;

/**
 * What the signed-in user may see. ADMIN: everything ({@link #ALL}). MANAGER: the engineers of their teams.
 * ENGINEER: their own rows, plus team-level views of their own team.
 *
 * @param teamIds    teams visible as a whole; null means every team
 * @param engineerId when set, the only engineer whose rows are visible
 */
public record DataScope(Set<Long> teamIds, Long engineerId) {

    public static final DataScope ALL = new DataScope(null, null);

    public static DataScope teams(Set<Long> teamIds) {
        return new DataScope(Set.copyOf(teamIds), null);
    }

    public static DataScope engineer(long engineerId, long teamId) {
        return new DataScope(Set.of(teamId), engineerId);
    }

    public boolean isAll() {
        return teamIds == null && engineerId == null;
    }

    /** Team-level data: the team's fairness, its name and settings. */
    public boolean canSeeTeam(long teamId) {
        return teamIds == null || teamIds.contains(teamId);
    }

    /** One engineer's own data: their alerts, shifts, burnout. */
    public boolean canSeeEngineer(long engineerId, long teamId) {
        return this.engineerId != null ? this.engineerId == engineerId : canSeeTeam(teamId);
    }

    /**
     * Limits a query to the rows this scope may see. {@code engineerOf} leads from the queried entity to the
     * engineer the row belongs to; rows without an engineer are visible only to {@link #ALL}.
     */
    public <T> Specification<T> restrict(Function<Root<T>, Path<?>> engineerOf) {
        return (root, query, cb) -> {
            if (isAll()) {
                return null;
            }
            Path<?> engineer = engineerOf.apply(root);
            if (engineerId != null) {
                return cb.equal(engineer.get("id"), engineerId);
            }
            return teamIds.isEmpty() ? cb.disjunction() : engineer.get("team").get("id").in(teamIds);
        };
    }
}
