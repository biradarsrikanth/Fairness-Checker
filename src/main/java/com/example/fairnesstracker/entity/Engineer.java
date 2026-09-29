package com.example.fairnesstracker.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "engineer_data")
@Data
@NoArgsConstructor

//Table Containing Engineer Data
public class Engineer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(unique = true)
    private String pagerDutyUserId;

    @NotBlank(message = "Name Cannot be Empty")
    private String name;

    // Unique regardless of case (ux_engineer_email)
    @Email(message = "Email Not Valid!")
    @NotBlank(message = "Email is required")
    private String email;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    // Engineers who left the rotation stay for history; inactive ones don't count as "zero alerts"
    @Column(nullable = false)
    private boolean active = true;

    // Excluded from toString/equals/hashCode: AlertEvent points back here, which would recurse
    @OneToMany(mappedBy = "engineer")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<AlertEvent> alerts = new ArrayList<>();
}
