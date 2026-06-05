package com.start.getemployed.vacancy.repository;

import com.start.getemployed.entity.Recommendation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

  Page<Recommendation> findByUserIdAndScoreGreaterThanEqual(
      Long userId, Short minScore, Pageable pageable);

  Optional<Recommendation> findByUserIdAndVacancyId(Long userId, Long vacancyId);

  List<Recommendation> findTop5ByUserIdOrderByScoreDesc(Long userId);

  void deleteByUserId(Long userId);

  @Query("select count(r) from Recommendation r where r.user.id = :userId")
  long countByUserId(Long userId);
}
