package com.start.getemployed.auth.controller.password_reset;

import com.start.getemployed.ResetRequest;
import com.start.getemployed.auth.service.AuthService;
import com.start.getemployed.auth.service.PasswordResetService;
import com.start.getemployed.auth.service.UserService;
import com.start.getemployed.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
@RestController
@RequestMapping("/api/v1/password")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final UserService userService;

    @PostMapping("/reset-request")
    public ResponseEntity<Map<String, String>> resetRequest(@RequestBody ResetRequest request) {

        userService.findByEmailOptional(request.email())
                .ifPresent(passwordResetService::createAndSend);

        return ResponseEntity.accepted()
                .body(Map.of("status", "if_exists_sent"));
    }
}




    // @PostMapping("/auth/password/reset")

