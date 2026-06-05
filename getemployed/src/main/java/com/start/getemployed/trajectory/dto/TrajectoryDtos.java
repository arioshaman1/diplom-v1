package com.start.getemployed.trajectory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TrajectoryDtos {

  public record TrajectoryResponse(
      Long id,
      Long userId,
      Long vacancyId,
      String vacancyTitle,
      int totalSteps,
      int completedSteps,
      int progressPercent,
      int totalWeeks,
      int currentWeek,
      LocalDateTime createdAt,
      LocalDateTime estimatedCompletionAt) {}

  public record StepResponse(
      Long id,
      Long trajectoryId,
      Long skillId,
      String skillName,
      String title,
      String description,
      String type,
      Short estimatedHours,
      Short week,
      Short order,
      String status,
      Short progressPercent,
      String note,
      LocalDate deadline,
      LocalDateTime completedAt,
      List<ResourceResponse> resources) {}

  public record ResourceResponse(String title, String url, String type) {}

  public record WeekStepResponse(
      Long id,
      String title,
      String skillName,
      Short estimatedHours,
      String status,
      Short progressPercent,
      LocalDate deadline) {}

  public record GenerateRequest(
      @Min(5) @Max(40) Short hoursPerWeek,
      @Min(2) @Max(8) Short weeks,
      String focus,
      String userRequest) {}

  public record GenerateResponse(
      Long trajectoryId,
      int totalSteps,
      int weeks,
      List<String> skills,
      List<GeneratedStep> steps) {}

  public record AiStatusResponse(boolean available, String model, String baseUrl, String message) {}

  public record GeneratedStep(
      Long id,
      String title,
      String skillName,
      Short week,
      Short order,
      Short estimatedHours,
      String status) {}

  public record PatchStepRequest(
      String status, @Min(0) @Max(100) Short progressPercent, String note) {}

  public record PatchStepResponse(StepStatus step, TrajectoryStatus trajectory) {}

  public record StepStatus(Long id, String status, LocalDateTime completedAt) {}

  public record TrajectoryStatus(int progressPercent, int completedSteps, int totalSteps) {}

  public record PutStepRequest(LocalDate deadline, @Min(1) Short estimatedHours, String note) {}

  public record HistoryItem(
      Long id,
      String vacancyTitle,
      int completedSteps,
      int totalSteps,
      int progressPercent,
      LocalDate startedAt,
      LocalDate completedAt) {}
}
