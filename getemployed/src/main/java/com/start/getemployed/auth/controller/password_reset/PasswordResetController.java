package com.start.getemployed.auth.controller.password_reset;

import com.start.getemployed.ResetRequest;
import com.start.getemployed.auth.service.PasswordResetService;
import com.start.getemployed.auth.service.UserService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/password")
@RequiredArgsConstructor
public class PasswordResetController {

  private final PasswordResetService passwordResetService;
  private final UserService userService;

  @PostMapping("/reset-request")
  public ResponseEntity<Map<String, String>> resetRequest(@RequestBody ResetRequest request) {

    userService.findByEmailOptional(request.email()).ifPresent(passwordResetService::createAndSend);

    return ResponseEntity.accepted().body(Map.of("status", "if_exists_sent"));
  }

  @PostMapping("/auth/password/reset")
  public ResponseEntity<Map<String, String>> resetPassword(
      @RequestParam String token, @RequestBody Map<String, String> body) {
    String newPassword = body.get("newPassword");

    passwordResetService.resetPassword(token, newPassword);

    return ResponseEntity.ok(Map.of("status", "password_updated"));
  }
}
