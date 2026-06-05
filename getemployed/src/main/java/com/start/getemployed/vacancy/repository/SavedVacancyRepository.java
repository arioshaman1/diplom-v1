package com.start.getemployed.vacancy.repository;

import com.start.getemployed.entity.SavedVacancy;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedVacancyRepository extends JpaRepository<SavedVacancy, Long> {

  Page<SavedVacancy> findByUserId(Long userId, Pageable pageable);

  Optional<SavedVacancy> findByUserIdAndVacancyId(Long userId, Long vacancyId);

  boolean existsByUserIdAndVacancyId(Long userId, Long vacancyId);
}
