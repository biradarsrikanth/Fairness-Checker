package com.example.fairnesstracker.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    // The engineer in the same query; teams are batch-loaded per page (AppUser.teams), since fetching a
    // collection here would make Hibernate page in memory
    @Override
    @EntityGraph(attributePaths = "engineer")
    Page<AppUser> findAll(Specification<AppUser> spec, Pageable pageable);

    // lower() on both sides matches the ux_app_user_email index (IgnoreCase would generate upper())
    @Query("SELECT u FROM AppUser u WHERE lower(u.email) = lower(:email)")
    Optional<AppUser> findByEmail(@Param("email") String email);

    @Query("SELECT count(u) > 0 FROM AppUser u WHERE lower(u.email) = lower(:email)")
    boolean existsByEmail(@Param("email") String email);

    // Everything that decides what the user may see, in one query
    @EntityGraph(attributePaths = {"teams", "engineer", "engineer.team"})
    Optional<AppUser> findWithAccessById(Long id);

    boolean existsByEngineer_Id(Long engineerId);

    Optional<AppUser> findByEngineer_Id(Long engineerId);

    // Locks the enabled admins until the transaction ends, so two admins can't disable each other at once
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM AppUser u WHERE u.role = :role AND u.enabled = true")
    List<AppUser> lockEnabledByRole(@Param("role") Role role);

    // AccessPolicy: is this team one of the manager's?
    boolean existsByIdAndTeams_Id(Long userId, Long teamId);
}
