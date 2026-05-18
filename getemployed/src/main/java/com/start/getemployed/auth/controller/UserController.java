package com.start.getemployed.auth.controller;

import com.start.getemployed.auth.dto.UserDto;
import com.start.getemployed.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/")
@RequiredArgsConstructor
@Tag(name = "Пользователи", description = "API для управления пользователями")
public class UserController {

  private final AuthService authService;

  @PostMapping
  @Operation(
      summary = "Создать нового пользователя",
      description = "Регистрация нового пользователя в системе")
  public ResponseEntity<UserDto.Response> createUser(
      @Valid @RequestBody UserDto.CreateRequest request) {
    UserDto.Response response = authService.createUser(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  //  @GetMapping("/{id}")
  //  @Operation(summary = "Получить пользователя по ID")
  //  public ResponseEntity<UserDto.Response> getUserById(@PathVariable Long id) {
  //    UserDto.Response response = authService.getUserById(id);
  //    return ResponseEntity.ok(response);
  //  }

  @GetMapping("/email/{email}")
  @Operation(summary = "Получить пользователя по email")
  public ResponseEntity<UserDto.Response> getUserByEmail(@PathVariable String email) {
    UserDto.Response response = authService.getUserByEmail(email);
    return ResponseEntity.ok(response);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Обновить данные пользователя")
  public ResponseEntity<UserDto.Response> updateUser(
      @PathVariable Long id, @Valid @RequestBody UserDto.UpdateRequest request) {
    UserDto.Response response = authService.updateUser(id, request);
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Удалить пользователя")
  public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    authService.deleteUser(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{userId}/roles/{roleName}")
  @Operation(summary = "Добавить роль пользователю")
  public ResponseEntity<UserDto.Response> addRoleToUser(
      @PathVariable Long userId, @PathVariable String roleName) {
    UserDto.Response response = authService.addRoleToUser(userId, roleName);
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{userId}/roles/{roleName}")
  @Operation(summary = "Удалить роль у пользователя")
  public ResponseEntity<UserDto.Response> removeRoleFromUser(
      @PathVariable Long userId, @PathVariable String roleName) {
    UserDto.Response response = authService.removeRoleFromUser(userId, roleName);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/health")
  @Operation(summary = "Проверка работоспособности API пользователей")
  public ResponseEntity<String> healthCheck() {
    return ResponseEntity.ok("User API работает корректно");
  }
}
