package com.start.getemployed.auth;


public class AuthDto {

    public record LoginResponse(String accessToken, UserDto user) {}
    public record LoginRequest(String email, String password) {}
    public record UserDto(String id, String email, String name) {}
}
