package com.start.getemployed.profile.service;

import com.start.getemployed.entity.Profile;
import com.start.getemployed.entity.Trajectory;
import com.start.getemployed.entity.User;
import com.start.getemployed.profile.dto.ProfileDto;
import com.start.getemployed.profile.dto.UpdateProfileRequest;
import com.start.getemployed.profile.mapper.ProfileMapper;
import com.start.getemployed.profile.repository.ProfileRepository;
import com.start.getemployed.skills.repository.UserSkillRepository;
import com.start.getemployed.trajectory.repository.TrajectoryRepository;
import com.start.getemployed.trajectory.repository.TrajectoryStepRepository;
import com.start.getemployed.vacancy.repository.RecommendationRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ProfileService {

  private final RedisTemplate<String, Object> redisTemplate;
  private final ProfileRepository profileRepository;
  private final ProfileMapper profileMapper;
  private final UserSkillRepository userSkillRepository;
  private final RecommendationRepository recommendationRepository;
  private final TrajectoryRepository trajectoryRepository;
  private final TrajectoryStepRepository stepRepository;

  @Transactional
  public ProfileDto get(User user) {
    return profileMapper.toDto(findOrCreate(user));
  }

  @Transactional
  public ProfileDto update(User user, UpdateProfileRequest request) {
    validate(request);
    Profile profile = findOrCreate(user);
    profileMapper.updateEntityFromRequest(request, profile);
    profile.setOnboardingComplete(true);
    Profile saved = profileRepository.save(profile);
    redisTemplate.delete("profile:" + user.getId());
    redisTemplate.delete("dashboard:" + user.getId());
    return profileMapper.toDto(saved);
  }

  @Transactional(readOnly = true)
  public Stats stats(User user) {
    // Match percent: top recommendation score for the user
    int matchPercent =
        recommendationRepository.findTop5ByUserIdOrderByScoreDesc(user.getId()).stream()
            .findFirst()
            .map(r -> (int) r.getScore())
            .orElse(0);

    // Skills: actual user skill count and how many are proficient (level >= 3)
    long totalSkills = userSkillRepository.countByUserId(user.getId());
    long coveredSkills =
        userSkillRepository.countByUserIdAndLevelGreaterThanEqual(user.getId(), (short) 3);

    // Trajectory progress from active trajectory
    int trajectoryProgress = 0;
    int weeklyGoalProgress = 0;
    try {
      Trajectory active =
          trajectoryRepository
              .findFirstByUserIdAndActiveTrueOrderByCreatedAtDesc(user.getId())
              .orElse(null);
      if (active != null && active.getTotalSteps() != null && active.getTotalSteps() > 0) {
        trajectoryProgress =
            (int) Math.round((double) active.getCompletedSteps() * 100 / active.getTotalSteps());

        // Current week steps completion
        short currentWeek =
            (short) Math.min(Math.max(1, active.getCompletedSteps() / 5 + 1), active.getWeeks());
        var weekSteps =
            stepRepository.findByTrajectoryIdAndWeekOrderByOrderAsc(active.getId(), currentWeek);
        if (!weekSteps.isEmpty()) {
          long completedInWeek =
              weekSteps.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
          weeklyGoalProgress = (int) Math.round((double) completedInWeek * 100 / weekSteps.size());
        }
      }
    } catch (Exception ignored) {
      // no active trajectory
    }

    // Days active: days since user account creation
    int daysActive = 1;
    if (user.getCreatedAt() != null) {
      daysActive =
          (int)
              Math.max(
                  1,
                  ChronoUnit.DAYS.between(user.getCreatedAt().toLocalDate(), LocalDate.now()) + 1);
    }

    return new Stats(
        matchPercent,
        totalSkills,
        coveredSkills,
        trajectoryProgress,
        weeklyGoalProgress,
        daysActive);
  }

  private Profile findOrCreate(User user) {
    return profileRepository
        .findByUserId(user.getId())
        .orElseGet(
            () -> {
              Profile profile = new Profile();
              profile.setUser(user);
              profile.setRemote(false);
              profile.setOnboardingComplete(false);
              return profileRepository.save(profile);
            });
  }

  private void validate(UpdateProfileRequest request) {
    if (request.salaryMin() != null
        && request.salaryMax() != null
        && request.salaryMin() > request.salaryMax()) {
      throw new IllegalArgumentException("salaryMin must be less than or equal to salaryMax");
    }
    if (request.level() != null && !request.level().matches("INTERN|JUNIOR|MIDDLE|SENIOR")) {
      throw new IllegalArgumentException("level must be INTERN, JUNIOR, MIDDLE or SENIOR");
    }
  }

  public record Stats(
      int matchPercent,
      long totalSkills,
      long coveredSkills,
      int trajectoryProgress,
      int weeklyGoalProgress,
      int daysActive) {}
}
