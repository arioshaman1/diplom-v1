package com.start.getemployed.trajectory.repository;

import com.start.getemployed.entity.StepResource;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StepResourceRepository extends JpaRepository<StepResource, Long> {

  List<StepResource> findByStepId(Long stepId);

  @Modifying
  @Query(
      value =
          "delete from step_resources where step_id in "
              + "(select id from trajectory_steps where trajectory_id = :trajectoryId)",
      nativeQuery = true)
  void deleteByTrajectoryId(Long trajectoryId);
}
