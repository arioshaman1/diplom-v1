package com.start.getemployed.trajectory.repository;

import com.start.getemployed.entity.Trajectory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TrajectoryRepository extends JpaRepository<Trajectory, Long> {

  Optional<Trajectory> findFirstByUserIdAndActiveTrueOrderByCreatedAtDesc(Long userId);

  List<Trajectory> findByUserIdAndActiveFalseOrderByCreatedAtDesc(Long userId);

  @Modifying
  @Query(
      value = "delete from trajectories where id = :id and user_id = :userId",
      nativeQuery = true)
  void deleteByIdAndUserId(Long id, Long userId);
}
