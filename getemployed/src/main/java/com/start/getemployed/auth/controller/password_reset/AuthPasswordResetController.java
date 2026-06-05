package com.start.getemployed.auth.controller.password_reset;

import com.start.getemployed.ResetRequest;
import com.start.getemployed.auth.ApiResponse;
import com.start.getemployed.auth.service.PasswordResetService;
import com.start.getemployed.auth.service.UserService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/password")
@RequiredArgsConstructor
public class AuthPasswordResetController {

  private final PasswordResetService passwordResetService;
  private final UserService userService;

  @PostMapping("/reset-request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void resetRequest(@RequestBody ResetRequest request) {
    userService.findByEmailOptional(request.email()).ifPresent(passwordResetService::createAndSend);
  }

  @PostMapping("/reset")
  public ApiResponse<Map<String, Boolean>> resetPassword(
      @RequestParam String token, @RequestBody Map<String, String> body) {
    passwordResetService.resetPassword(token, body.get("newPassword"));
    return new ApiResponse<>(Map.of("success", true));
  }
}
