package com.start.getemployed.common.api;

import com.start.getemployed.auth.repository.UserRepository;
import com.start.getemployed.entity.User;
import com.start.getemployed.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserContext {

  private final UserRepository userRepository;

  public User currentUser(Authentication authentication) {
    if (authentication == null || authentication.getName() == null) {
      throw new ResourceNotFoundException("Текущий пользователь не найден");
    }
    return userRepository
        .findByEmail(authentication.getName().toLowerCase().trim())
        .orElseThrow(() -> new ResourceNotFoundException("Текущий пользователь не найден"));
  }
}
