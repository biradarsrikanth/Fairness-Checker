package com.example.fairnesstracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "team")
@Getter
@Setter
@NoArgsConstructor
public class Team {

    public static final String DEFAULT_TIMEZONE = "Asia/Kolkata";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    // IANA timezone; decides what counts as night/weekend for this team's engineers
    @Column(nullable = false, length = 64)
    private String timezone = DEFAULT_TIMEZONE;

    public Team(String name, String timezone) {
        this.name = name;
        this.timezone = timezone;
    }
}
