package com.start.getemployed.skills.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public class SkillDtos {

  public record UserSkillResponse(
      Long id,
      Long skillId,
      String name,
      Short level,
      Boolean verified,
      String category,
      LocalDateTime updatedAt) {}

  public record AddSkillRequest(@NotBlank String name, @Min(0) @Max(5) Short level) {}

  public record UpdateSkillRequest(@Min(0) @Max(5) Short level) {}

  public record SkillSearchResponse(Long id, String name, String category, Double popularity) {}
}
