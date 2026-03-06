package com.start.getemployed.service;

import com.start.getemployed.entity.Role;
import com.start.getemployed.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailService {
  @Bean
  public UserDetailsService userDetailsService(UserRepository repo) {
    return email ->
        repo.findByEmail(email.toLowerCase())
            .map(
                u ->
                    org.springframework.security.core.userdetails.User.withUsername(u.getEmail())
                        .password(u.getPasswordHash())
                        .disabled(!u.isEnabled())
                        .authorities(
                            u.getRoles().stream().map(Role::getName).toArray(String[]::new))
                        .build())
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
  }
}
