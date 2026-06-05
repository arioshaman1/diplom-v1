package com.start.getemployed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "trajectory_steps")
@Getter
@Setter
@NoArgsConstructor
public class TrajectoryStep {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trajectory_id", nullable = false)
  private Trajectory trajectory;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "skill_id")
  private Skill skill;

  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  private String type = "LEARN";

  @Column(name = "estimated_hours")
  private Short estimatedHours;

  private Short week;

  @Column(name = "\"order\"")
  private Short order;

  private String status = "PENDING";

  @Column(name = "progress_percent")
  private Short progressPercent = 0;

  @Column(columnDefinition = "TEXT")
  private String note;

  private LocalDate deadline;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
