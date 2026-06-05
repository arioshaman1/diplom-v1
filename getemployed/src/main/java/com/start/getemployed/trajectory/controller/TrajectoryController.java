package com.start.getemployed.trajectory.controller;

import com.start.getemployed.common.api.ApiEnvelope;
import com.start.getemployed.common.api.UserContext;
import com.start.getemployed.trajectory.dto.TrajectoryDtos;
import com.start.getemployed.trajectory.service.TrajectoryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trajectory")
@RequiredArgsConstructor
public class TrajectoryController {

  private final TrajectoryService trajectoryService;
  private final UserContext userContext;

  @GetMapping
  public ApiEnvelope<TrajectoryDtos.TrajectoryResponse> active(Authentication authentication) {
    return ApiEnvelope.of(trajectoryService.active(userContext.currentUser(authentication)));
  }

  @GetMapping("/ai/status")
  public ApiEnvelope<TrajectoryDtos.AiStatusResponse> aiStatus() {
    return ApiEnvelope.of(trajectoryService.aiStatus());
  }

  @GetMapping("/steps")
  public ApiEnvelope<List<TrajectoryDtos.StepResponse>> steps(
      Authentication authentication,
      @RequestParam(required = false) Short week,
      @RequestParam(required = false) String status) {
    return ApiEnvelope.of(
        trajectoryService.steps(userContext.currentUser(authentication), week, status));
  }

  @GetMapping("/steps/{stepId}/resources/live")
  public ApiEnvelope<List<TrajectoryDtos.ResourceResponse>> liveResources(
      Authentication authentication, @PathVariable Long stepId) {
    return ApiEnvelope.of(
        trajectoryService.liveResources(userContext.currentUser(authentication), stepId));
  }

  @GetMapping("/current-week")
  public ApiEnvelope<List<TrajectoryDtos.WeekStepResponse>> currentWeek(
      Authentication authentication) {
    return ApiEnvelope.of(trajectoryService.currentWeek(userContext.currentUser(authentication)));
  }

  @PostMapping("/generate")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiEnvelope<TrajectoryDtos.GenerateResponse> generate(
      Authentication authentication,
      @RequestParam Long vacancyId,
      @Valid @RequestBody(required = false) TrajectoryDtos.GenerateRequest request) {
    TrajectoryDtos.GenerateRequest body =
        request == null
            ? new TrajectoryDtos.GenerateRequest((short) 15, (short) 4, null, null)
            : request;
    return ApiEnvelope.of(
        trajectoryService.generate(userContext.currentUser(authentication), vacancyId, body));
  }

  @PatchMapping("/steps/{stepId}")
  public ApiEnvelope<TrajectoryDtos.PatchStepResponse> patch(
      Authentication authentication,
      @PathVariable Long stepId,
      @Valid @RequestBody TrajectoryDtos.PatchStepRequest request) {
    return ApiEnvelope.of(
        trajectoryService.patch(userContext.currentUser(authentication), stepId, request));
  }

  @PutMapping("/steps/{stepId}")
  public ApiEnvelope<TrajectoryDtos.StepResponse> put(
      Authentication authentication,
      @PathVariable Long stepId,
      @Valid @RequestBody TrajectoryDtos.PutStepRequest request) {
    return ApiEnvelope.of(
        trajectoryService.put(userContext.currentUser(authentication), stepId, request));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(Authentication authentication) {
    trajectoryService.deleteActive(userContext.currentUser(authentication));
  }

  @GetMapping("/history")
  public ApiEnvelope<List<TrajectoryDtos.HistoryItem>> history(Authentication authentication) {
    return ApiEnvelope.of(trajectoryService.history(userContext.currentUser(authentication)));
  }
}
