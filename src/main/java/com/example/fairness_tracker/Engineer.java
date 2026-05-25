package com.example.fairness_tracker;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Engineer {

    @Id
    private Long id;
    private String name;
    private String Email;
    private String Team;
}
