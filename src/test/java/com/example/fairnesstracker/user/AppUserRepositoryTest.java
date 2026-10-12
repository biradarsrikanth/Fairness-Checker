package com.example.fairnesstracker.user;

import com.example.fairnesstracker.engineer.Engineer;
import com.example.fairnesstracker.engineer.EngineerRepository;
import com.example.fairnesstracker.team.Team;
import com.example.fairnesstracker.team.TeamRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DataJpaTest
class AppUserRepositoryTest {

    @Autowired private AppUserRepository users;
    @Autowired private TeamRepository teams;
    @Autowired private EngineerRepository engineers;
    @Autowired private EntityManager entityManager;

    @Test
    void findsByEmailRegardlessOfCase() {
        users.save(user("Priya.Admin@Example.com", Role.ADMIN));

        assertThat(users.findByEmail("priya.admin@example.com"))
                .get().extracting(AppUser::getEmail).isEqualTo("Priya.Admin@Example.com");
        assertThat(users.existsByEmail("PRIYA.ADMIN@EXAMPLE.COM")).isTrue();
        assertThat(users.findByEmail("someone@example.com")).isEmpty();
    }

    @Test
    void managerTeamAccess() {
        Team sre = teams.save(new Team("SRE", "Asia/Kolkata"));
        Team platform = teams.save(new Team("Platform", "Asia/Kolkata"));
        AppUser manager = user("manager@example.com", Role.MANAGER);
        manager.getTeams().add(sre);
        users.save(manager);
        entityManager.flush();
        entityManager.clear();

        assertThat(users.existsByIdAndTeams_Id(manager.getId(), sre.getId())).isTrue();
        assertThat(users.existsByIdAndTeams_Id(manager.getId(), platform.getId())).isFalse();
        assertThat(users.findById(manager.getId()).orElseThrow().getTeams())
                .extracting(Team::getName).containsExactly("SRE");
    }

    @Test
    void engineerLoginPointsAtItsEngineer() {
        Engineer asha = new Engineer();
        asha.setName("Asha");
        asha.setEmail("asha@example.com");
        asha.setTeam(teams.save(new Team("SRE", "Asia/Kolkata")));
        engineers.save(asha);
        AppUser login = user("asha@example.com", Role.ENGINEER);
        login.setEngineer(asha);
        users.save(login);
        entityManager.flush();
        entityManager.clear();

        assertThat(users.existsByEngineer_Id(asha.getId())).isTrue();
        AppUser found = users.findByEmail("asha@example.com").orElseThrow();
        assertThat(found.getEngineer().getName()).isEqualTo("Asha");
        assertThat(found.isEnabled()).isTrue();
        assertThat(found.getFailedLoginCount()).isZero();
        assertThat(found.isMustChangePassword()).isFalse();
    }

    private static AppUser user(String email, Role role) {
        AppUser user = new AppUser(email, "Test User", role);
        user.setPassword("{noop}secret", Instant.parse("2026-10-01T00:00:00Z"));
        return user;
    }
}
