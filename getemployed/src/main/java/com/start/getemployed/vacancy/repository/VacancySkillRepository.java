package com.start.getemployed.vacancy.repository;

import com.start.getemployed.entity.VacancySkill;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VacancySkillRepository extends JpaRepository<VacancySkill, Long> {

  List<VacancySkill> findByVacancyId(Long vacancyId);

  boolean existsByVacancyIdAndSkillId(Long vacancyId, Long skillId);

  void deleteByVacancyId(Long vacancyId);
}
