package com.start.getemployed.trajectory.repository;

import com.start.getemployed.entity.TrajectoryStep;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TrajectoryStepRepository extends JpaRepository<TrajectoryStep, Long> {

  List<TrajectoryStep> findByTrajectoryIdOrderByWeekAscOrderAsc(Long trajectoryId);

  List<TrajectoryStep> findByTrajectoryIdAndWeekOrderByOrderAsc(Long trajectoryId, Short week);

  Optional<TrajectoryStep> findByIdAndUserId(Long id, Long userId);

  long countByTrajectoryIdAndStatus(Long trajectoryId, String status);

  @Modifying
  @Query(
      value = "delete from trajectory_steps where trajectory_id = :trajectoryId",
      nativeQuery = true)
  void deleteByTrajectoryId(Long trajectoryId);
}
