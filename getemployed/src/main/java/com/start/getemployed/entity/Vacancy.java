package com.start.getemployed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "vacancies")
@Getter
@Setter
@NoArgsConstructor
public class Vacancy {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "hh_id", nullable = false, unique = true)
  private String hhId;

  @Column(nullable = false)
  private String title;

  private String employer;
  private String area;

  @Column(name = "area_id")
  private Integer areaId;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(columnDefinition = "TEXT")
  private String requirements;

  @Column(name = "salary_min")
  private Integer salaryMin;

  @Column(name = "salary_max")
  private Integer salaryMax;

  private String currency;
  private Boolean remote = false;
  private String experience;
  private String url;

  @CreationTimestamp
  @Column(name = "fetched_at", updatable = false)
  private LocalDateTime fetchedAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
