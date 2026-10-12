package com.example.fairnesstracker.auth;

import com.example.fairnesstracker.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

/**
 * One refresh token, stored as the SHA-256 of the value the client holds. Single use: refreshing revokes it
 * and records its replacement. All tokens descending from one login share a {@code familyId}.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AppUser user;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    // Set when the token was rotated; revoked without a replacement means logged out or revoked
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private RefreshToken replacedBy;

    @Column(length = 45)
    private String createdIp;

    @Column(length = 512)
    private String userAgent;

    // Set by the application's clock (not the column default) so it can't be later than expiresAt
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    RefreshToken(AppUser user, String tokenHash, UUID familyId, Instant createdAt, Instant expiresAt,
                 String createdIp, String userAgent) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.createdIp = createdIp;
        this.userAgent = userAgent;
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    void rotatedTo(RefreshToken replacement, Instant at) {
        this.replacedBy = replacement;
        this.revokedAt = at;
    }

    void revoke(Instant at) {
        if (revokedAt == null) {
            revokedAt = at;
        }
    }
}
