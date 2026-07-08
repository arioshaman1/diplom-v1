package com.start.getemployed.vacancy.dto;

import java.time.LocalDateTime;
import java.util.List;

public class VacancyDtos {

  public record VacancyListItem(
      Long id,
      String hhId,
      String title,
      String employer,
      String area,
      Integer salaryMin,
      Integer salaryMax,
      Boolean remote,
      int score,
      List<String> gaps,
      LocalDateTime fetchedAt) {}

  public record VacancyDetail(
      Long id,
      String hhId,
      String title,
      String employer,
      String description,
      String requirements,
      String area,
      Integer salaryMin,
      Integer salaryMax,
      Boolean remote,
      int score,
      int sbertScore,
      int skillsCoverage,
      List<VacancySkillItem> skills,
      String url,
      LocalDateTime fetchedAt) {}

  public record VacancySkillItem(
      Long skillId,
      String name,
      String importance,
      int requiredLevel,
      int userLevel,
      boolean covered) {}

  public record ImportAccepted(String jobId, String status, int estimatedCount) {}

  public record ImportStatus(
      String jobId, String status, int imported, int errors, String message) {
    public ImportStatus(String jobId, String status, int imported, int errors) {
      this(jobId, status, imported, errors, null);
    }
  }

  public record AreaOption(Integer id, String name) {}

  public record SavedAt(LocalDateTime savedAt) {}

  public record RecommendationItem(
      Long vacancyId,
      int score,
      int sbertScore,
      int skillsCoverage,
      List<Gap> gaps,
      VacancySummary vacancy,
      LocalDateTime updatedAt) {}

  public record Gap(String skill, String importance) {}

  public record VacancySummary(
      String title, String employer, String area, Integer salaryMin, Integer salaryMax) {}

  public record RecommendationDetail(
      Long vacancyId,
      int score,
      int sbertScore,
      int skillsCoverage,
      MatchBreakdown matchBreakdown,
      List<String> coveredSkills,
      List<DetailedGap> gaps) {}

  public record MatchBreakdown(double sbertWeight, double skillsWeight, String formula) {}

  public record DetailedGap(
      Long skillId,
      String skill,
      String importance,
      int userLevel,
      int requiredLevel,
      int affectedVacanciesPercent) {}

  public record TopSkill(Long skillId, String name, double frequency, long vacancyCount) {}

  public record SkillGap(
      Long skillId,
      String name,
      String importance,
      int userLevel,
      int requiredLevel,
      double frequency,
      long vacancyCount,
      int affectedPercent) {}

  public record RebuildAccepted(String jobId, String status, long vacancyCount) {}

  public record ExplainResponse(String explanation, boolean aiGenerated) {}
}
