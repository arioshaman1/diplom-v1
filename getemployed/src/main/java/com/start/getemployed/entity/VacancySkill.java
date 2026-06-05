package com.start.getemployed.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "vacancy_skills",
    uniqueConstraints = @UniqueConstraint(columnNames = {"vacancy_id", "skill_id"}))
@Getter
@Setter
@NoArgsConstructor
public class VacancySkill {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vacancy_id", nullable = false)
  private Vacancy vacancy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "skill_id", nullable = false)
  private Skill skill;

  private String importance = "NICE_TO_HAVE";
}
