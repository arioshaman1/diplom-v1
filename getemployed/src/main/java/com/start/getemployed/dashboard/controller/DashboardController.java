package com.start.getemployed.dashboard.controller;

import com.start.getemployed.common.api.ApiEnvelope;
import com.start.getemployed.common.api.UserContext;
import com.start.getemployed.entity.User;
import com.start.getemployed.profile.service.ProfileService;
import com.start.getemployed.trajectory.service.TrajectoryService;
import com.start.getemployed.vacancy.dto.VacancyDtos;
import com.start.getemployed.vacancy.service.VacancyService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final UserContext userContext;
  private final ProfileService profileService;
  private final VacancyService vacancyService;
  private final TrajectoryService trajectoryService;

  @GetMapping("/overview")
  public ApiEnvelope<Overview> overview(Authentication authentication) {
    User user = userContext.currentUser(authentication);
    ProfileService.Stats stats = profileService.stats(user);
    List<VacancyDtos.TopSkill> topSkills = vacancyService.topSkills(user, 5);
    List<VacancyDtos.SkillGap> skillGaps = vacancyService.skillGaps(user, 0.0);
    List<?> weekPlan;
    try {
      weekPlan = trajectoryService.currentWeek(user);
    } catch (RuntimeException ex) {
      weekPlan = List.of();
    }
    return ApiEnvelope.of(
        new Overview(
            stats.matchPercent(),
            vacancyService.userVacancyCount(user),
            null,
            topSkills.stream().map(s -> new SkillFrequency(s.name(), s.frequency())).toList(),
            skillGaps,
            new RadarData(List.of()),
            weekPlan,
            vacancyService.topRecommendations(user, 5)));
  }

  public record Overview(
      int matchPercent,
      long totalVacancies,
      LocalDateTime lastImportedAt,
      List<SkillFrequency> topSkills,
      List<VacancyDtos.SkillGap> skillGaps,
      RadarData radarData,
      List<?> weekPlan,
      List<?> topRecommendations) {}

  public record SkillFrequency(String name, double frequency) {}

  public record RadarData(List<?> userSkills) {}
}
