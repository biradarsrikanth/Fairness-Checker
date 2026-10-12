package com.example.fairnesstracker.user;

import com.example.fairnesstracker.engineer.Engineer;
import com.example.fairnesstracker.team.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A login. Not an engineer: admins and managers usually aren't on call, and an ENGINEER login points at
 * the engineer whose data it may see. Emails are unique regardless of case (ux_app_user_email) and kept as
 * entered.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(nullable = false)
    private String email;

    // Encoded with its algorithm prefix ({bcrypt}...); changed only through setPassword
    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    // Required for ENGINEER logins (ck_user_engineer_link); one login per engineer
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engineer_id", unique = true)
    private Engineer engineer;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private int failedLoginCount;

    private Instant lockedUntil;

    private Instant lastLoginAt;

    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private Instant passwordChangedAt;

    @Column(nullable = false)
    private boolean mustChangePassword;

    // MANAGER logins: the teams they can see. Unused for other roles
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_team_access",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id"))
    private Set<Team> teams = new HashSet<>();

    public AppUser(String email, String fullName, Role role) {
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }

    /** The only way to change the password: the hash and the time it changed go together. */
    public void setPassword(String passwordHash, Instant changedAt) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = changedAt;
    }
}
