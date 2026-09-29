package com.example.fairnesstracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A period an engineer was on call (UTC), from PagerDuty /oncalls. Level 1 is primary on call. */
@Entity
@Table(name = "oncall_shift")
@Getter
@Setter
@NoArgsConstructor
public class OncallShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "engineer_id")
    private Engineer engineer;

    private String pagerdutyEscalationPolicyId;

    private String pagerdutyScheduleId;

    @Column(nullable = false)
    private short escalationLevel = 1;

    @Column(nullable = false)
    private LocalDateTime startsAt;

    @Column(nullable = false)
    private LocalDateTime endsAt;
}
