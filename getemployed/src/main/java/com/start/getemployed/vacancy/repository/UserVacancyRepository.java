package com.start.getemployed.vacancy.repository;

import com.start.getemployed.entity.UserVacancy;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserVacancyRepository extends JpaRepository<UserVacancy, Long> {

  boolean existsByUserIdAndVacancyId(Long userId, Long vacancyId);

  List<UserVacancy> findByUserIdOrderByImportedAtDesc(Long userId);

  long countByUserId(Long userId);
}
