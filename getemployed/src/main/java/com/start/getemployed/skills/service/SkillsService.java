package com.start.getemployed.skills.service;

import com.start.getemployed.entity.Skill;
import com.start.getemployed.entity.User;
import com.start.getemployed.entity.UserSkill;
import com.start.getemployed.exception.ResourceAlreadyExistsException;
import com.start.getemployed.exception.ResourceNotFoundException;
import com.start.getemployed.skills.dto.SkillDtos;
import com.start.getemployed.skills.repository.SkillRepository;
import com.start.getemployed.skills.repository.UserSkillRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SkillsService {

  private final SkillRepository skillRepository;
  private final UserSkillRepository userSkillRepository;
  private final RedisTemplate<String, Object> redisTemplate;

  @Transactional(readOnly = true)
  public List<SkillDtos.UserSkillResponse> list(User user, String sort) {
    var skills = userSkillRepository.findByUserId(user.getId());
    if ("level,desc".equalsIgnoreCase(sort)) {
      skills.sort(Comparator.comparing(UserSkill::getLevel).reversed());
    }
    return skills.stream().map(this::toResponse).toList();
  }

  @Transactional
  public SkillDtos.UserSkillResponse add(User user, SkillDtos.AddSkillRequest request) {
    Skill skill =
        skillRepository
            .findByNameIgnoreCase(request.name().trim())
            .orElseGet(() -> createSkill(request.name().trim()));
    if (userSkillRepository.existsByUserIdAndSkillId(user.getId(), skill.getId())) {
      throw new ResourceAlreadyExistsException("Навык уже добавлен");
    }
    UserSkill userSkill = new UserSkill();
    userSkill.setUser(user);
    userSkill.setSkill(skill);
    userSkill.setLevel(request.level());
    userSkill.setVerified(false);
    invalidate(user.getId());
    return toResponse(userSkillRepository.save(userSkill));
  }

  @Transactional
  public SkillDtos.UserSkillResponse update(
      User user, Long userSkillId, SkillDtos.UpdateSkillRequest request) {
    UserSkill userSkill =
        userSkillRepository
            .findByIdAndUserId(userSkillId, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Навык пользователя не найден"));
    userSkill.setLevel(request.level());
    invalidate(user.getId());
    return toResponse(userSkillRepository.save(userSkill));
  }

  @Transactional
  public void delete(User user, Long userSkillId) {
    UserSkill userSkill =
        userSkillRepository
            .findByIdAndUserId(userSkillId, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Навык пользователя не найден"));
    userSkillRepository.delete(userSkill);
    invalidate(user.getId());
  }

  @Transactional(readOnly = true)
  public List<SkillDtos.SkillSearchResponse> search(String query) {
    return skillRepository.findTop10ByNameContainingIgnoreCaseOrderByPopularityDesc(query).stream()
        .map(
            s ->
                new SkillDtos.SkillSearchResponse(
                    s.getId(), s.getName(), s.getCategory(), s.getPopularity()))
        .toList();
  }

  private Skill createSkill(String name) {
    Skill skill = new Skill();
    skill.setName(name);
    skill.setCategory("OTHER");
    skill.setPopularity(0.0);
    return skillRepository.save(skill);
  }

  private SkillDtos.UserSkillResponse toResponse(UserSkill userSkill) {
    Skill skill = userSkill.getSkill();
    return new SkillDtos.UserSkillResponse(
        userSkill.getId(),
        skill.getId(),
        skill.getName(),
        userSkill.getLevel(),
        userSkill.getVerified(),
        skill.getCategory(),
        userSkill.getUpdatedAt());
  }

  private void invalidate(Long userId) {
    redisTemplate.delete("dashboard:" + userId);
    redisTemplate.delete("top-skills:" + userId);
    redisTemplate.delete("skill-gaps:" + userId);
  }
}
