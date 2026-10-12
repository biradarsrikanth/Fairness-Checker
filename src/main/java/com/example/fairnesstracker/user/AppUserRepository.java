package com.example.fairnesstracker.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // lower() on both sides matches the ux_app_user_email index (IgnoreCase would generate upper())
    @Query("SELECT u FROM AppUser u WHERE lower(u.email) = lower(:email)")
    Optional<AppUser> findByEmail(@Param("email") String email);

    @Query("SELECT count(u) > 0 FROM AppUser u WHERE lower(u.email) = lower(:email)")
    boolean existsByEmail(@Param("email") String email);

    boolean existsByEngineer_Id(Long engineerId);

    // AccessPolicy: is this team one of the manager's?
    boolean existsByIdAndTeams_Id(Long userId, Long teamId);
}
