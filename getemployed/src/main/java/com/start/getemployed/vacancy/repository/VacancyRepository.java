package com.start.getemployed.vacancy.repository;

import com.start.getemployed.entity.Vacancy;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VacancyRepository
    extends JpaRepository<Vacancy, Long>, JpaSpecificationExecutor<Vacancy> {

  Page<Vacancy> findByTitleContainingIgnoreCase(String title, Pageable pageable);

  Optional<Vacancy> findByHhId(String hhId);
}
