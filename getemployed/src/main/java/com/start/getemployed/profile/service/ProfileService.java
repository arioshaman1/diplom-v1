package com.start.getemployed.profile.service;

import com.start.getemployed.entity.Profile;
import com.start.getemployed.entity.User;
import com.start.getemployed.profile.dto.ProfileDto;
import com.start.getemployed.profile.dto.UpdateProfileRequest;
import com.start.getemployed.profile.mapper.ProfileMapper;
import com.start.getemployed.profile.repository.ProfileRepository;
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

  @Transactional(readOnly = true)
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
    Profile profile = findOrCreate(user);
    int covered =
        profile.getOnboardingComplete() != null && profile.getOnboardingComplete() ? 1 : 0;
    return new Stats(covered * 50, covered, covered, 0, 0, 1);
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
