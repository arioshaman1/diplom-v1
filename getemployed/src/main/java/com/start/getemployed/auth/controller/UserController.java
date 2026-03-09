package com.start.getemployed.auth.controller;

import com.start.getemployed.auth.dto.UserDto;
import com.start.getemployed.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Пользователи", description = "API для управления пользователями")
public class UserController {

  private final UserService userService;

  @PostMapping
  @Operation(
      summary = "Создать нового пользователя",
      description = "Регистрация нового пользователя в системе")
  public ResponseEntity<UserDto.Response> createUser(
      @Valid @RequestBody UserDto.CreateRequest request) {
    UserDto.Response response = userService.createUser(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Получить пользователя по ID")
  public ResponseEntity<UserDto.Response> getUserById(@PathVariable Long id) {
    UserDto.Response response = userService.getUserById(id);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/email/{email}")
  @Operation(summary = "Получить пользователя по email")
  public ResponseEntity<UserDto.Response> getUserByEmail(@PathVariable String email) {
    UserDto.Response response = userService.getUserByEmail(email);
    return ResponseEntity.ok(response);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Обновить данные пользователя")
  public ResponseEntity<UserDto.Response> updateUser(
      @PathVariable Long id, @Valid @RequestBody UserDto.UpdateRequest request) {
    UserDto.Response response = userService.updateUser(id, request);
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Удалить пользователя")
  public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    userService.deleteUser(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{userId}/roles/{roleName}")
  @Operation(summary = "Добавить роль пользователю")
  public ResponseEntity<UserDto.Response> addRoleToUser(
      @PathVariable Long userId, @PathVariable String roleName) {
    UserDto.Response response = userService.addRoleToUser(userId, roleName);
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{userId}/roles/{roleName}")
  @Operation(summary = "Удалить роль у пользователя")
  public ResponseEntity<UserDto.Response> removeRoleFromUser(
      @PathVariable Long userId, @PathVariable String roleName) {
    UserDto.Response response = userService.removeRoleFromUser(userId, roleName);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/health")
  @Operation(summary = "Проверка работоспособности API пользователей")
  public ResponseEntity<String> healthCheck() {
    return ResponseEntity.ok("User API работает корректно");
  }
}
