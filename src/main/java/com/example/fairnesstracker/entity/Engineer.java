package com.example.fairnesstracker.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;


@Entity
@Table(name = "engineer_data")
@Data
@AllArgsConstructor
@NoArgsConstructor

//Table Containing Engineer Data
public class Engineer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @NotBlank(message = "Name Cannot be Empty")
    private String name;

    @Email(message = "Email Not Valid!")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Team is required")
    private String team;
}
