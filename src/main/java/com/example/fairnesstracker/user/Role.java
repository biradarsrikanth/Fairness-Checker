package com.example.fairnesstracker.user;

/**
 * What a login may do. ADMIN: everything, including users and PagerDuty operations. MANAGER: the teams
 * listed in {@code user_team_access}. ENGINEER: their own data plus team-level fairness.
 */
public enum Role {
    ADMIN,
    MANAGER,
    ENGINEER
}
