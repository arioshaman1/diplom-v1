package com.start.getemployed.auth.service;

import com.start.getemployed.auth.dto.UserDto;
import com.start.getemployed.entity.Role;
import com.start.getemployed.entity.User;
import com.start.getemployed.exception.ResourceAlreadyExistsException;
import com.start.getemployed.exception.ResourceNotFoundException;
import com.start.getemployed.auth.repository.RoleRepository;
import com.start.getemployed.auth.repository.UserRepository;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public UserDto.Response createUser(UserDto.CreateRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new ResourceAlreadyExistsException("Пользователь с таким email уже существует");
    }
    User user = new User();
    user.setEmail(request.getEmail());
    user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    user.setName(request.getName());
    user.setEnabled(true);
    Role userRole =
        roleRepository
            .findByName(Role.ROLE_USER)
            .orElseThrow(() -> new ResourceNotFoundException("Роль USER не найдена"));
    user.addRole(userRole);

    User savedUser = userRepository.save(user);

    return mapToResponse(savedUser);
  }

  @Transactional(readOnly = true)
  public UserDto.Response getUserById(Long id) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
    return mapToResponse(user);
  }

  @Transactional(readOnly = true)
  public UserDto.Response getUserByEmail(String email) {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
    return mapToResponse(user);
  }

  @Transactional
  public UserDto.Response updateUser(Long id, UserDto.UpdateRequest request) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

    if (request.getName() != null) {
      user.setName(request.getName());
    }

    if (request.getEnabled() != null) {
      user.setEnabled(request.getEnabled());
    }

    User updatedUser = userRepository.save(user);
    return mapToResponse(updatedUser);
  }

  @Transactional(readOnly = true)
  public User findByEmailOrNull(String email) {
    return userRepository.findByEmail(email.toLowerCase().trim()).orElse(null);
  }

  @Transactional
  public void deleteUser(Long id) {
    if (!userRepository.existsById(id)) {
      throw new ResourceNotFoundException("Пользователь не найден");
    }
    userRepository.deleteById(id);
  }

  @Transactional
  public UserDto.Response addRoleToUser(Long userId, String roleName) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

    Role role =
        roleRepository
            .findByName(roleName)
            .orElseThrow(() -> new ResourceNotFoundException("Роль не найдена"));

    user.addRole(role);
    User updatedUser = userRepository.save(user);

    return mapToResponse(updatedUser);
  }

  @Transactional
  public UserDto.Response removeRoleFromUser(Long userId, String roleName) {
    User user =
        userRepository
            .findByIdWithRoles(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

    Role role =
        roleRepository
            .findByName(roleName)
            .orElseThrow(() -> new ResourceNotFoundException("Роль не найдена"));

    user.removeRole(role);
    User updatedUser = userRepository.save(user);

    return mapToResponse(updatedUser);
  }

  private UserDto.Response mapToResponse(User user) {
    UserDto.Response response = new UserDto.Response();
    response.setId(user.getId());
    response.setEmail(user.getEmail());
    response.setName(user.getName());
    response.setEnabled(user.isEnabled());
    response.setCreatedAt(user.getCreatedAt());
    response.setUpdatedAt(user.getUpdatedAt());

    Set<String> roleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
    response.setRoles(roleNames);

    return response;
  }

  @Transactional
  public User registerDisabled(String email, String password, String name) {
    String normalized = email.toLowerCase().trim();

    User existing = userRepository.findByEmail(normalized).orElse(null);
    if (existing != null) {
      if (existing.isEnabled()) {
        throw new IllegalArgumentException("email already in use");
      }

      existing.setPasswordHash(passwordEncoder.encode(password));
      existing.setName(name);

      return userRepository.save(existing);
    }

    User u = new User();
    u.setEmail(normalized);
    u.setPasswordHash(passwordEncoder.encode(password));
    u.setName(name);
    u.setEnabled(false);

    Role userRole =
        roleRepository
            .findByName(Role.ROLE_USER)
            .orElseThrow(() -> new ResourceNotFoundException("Роль USER не найдена"));
    u.addRole(userRole);

    return userRepository.save(u);
  }

  @Transactional
  public void enableByEmail(String email) {
    String normalized = email.toLowerCase().trim();

    User user =
        userRepository
            .findByEmail(normalized)
            .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

    if (!user.isEnabled()) {
      user.setEnabled(true);
      userRepository.save(user);
    }
  }
}
