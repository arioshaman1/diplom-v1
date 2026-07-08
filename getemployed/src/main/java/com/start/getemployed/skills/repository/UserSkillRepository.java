package com.start.getemployed.skills.repository;

import com.start.getemployed.entity.UserSkill;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {

  List<UserSkill> findByUserId(Long userId);

  Optional<UserSkill> findByIdAndUserId(Long id, Long userId);

  boolean existsByUserIdAndSkillId(Long userId, Long skillId);

  long countByUserId(Long userId);

  long countByUserIdAndLevelGreaterThanEqual(Long userId, Short level);
}
