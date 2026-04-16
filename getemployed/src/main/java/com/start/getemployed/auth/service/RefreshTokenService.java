package com.start.getemployed.auth.service;

import com.start.getemployed.entity.RefreshToken;
import com.start.getemployed.entity.Role;
import com.start.getemployed.entity.User;
import com.start.getemployed.auth.repository.RefreshTokenRepository;
import com.start.getemployed.auth.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RefreshTokenService {

  public record RefreshResult(String subject, Set<String> roles, String newRefreshToken) {}

  private static final Duration REFRESH_TTL = Duration.ofDays(30);
  private static final int TOKEN_BYTES = 64;

  private final RefreshTokenRepository refreshRepo;
  private final UserRepository userRepo;
  private final SecureRandom random = new SecureRandom();

  public RefreshTokenService(RefreshTokenRepository refreshRepo, UserRepository userRepo) {
    this.refreshRepo = refreshRepo;
    this.userRepo = userRepo;
  }

  @Transactional
  public String issue(String email) {

    User user =
        userRepo
            .findByEmail(email.toLowerCase())
            .orElseThrow(() -> new BadCredentialsException("User not found"));

    if (!user.isEnabled()) {
      throw new BadCredentialsException("User disabled");
    }

    String raw = generateRawToken();
    String hash = sha256Hex(raw);

    RefreshToken rt = new RefreshToken();
    rt.setUser(user);
    rt.setTokenHash(hash);
    rt.setCreatedAt(Instant.now());
    rt.setExpiresAt(Instant.now().plus(REFRESH_TTL));

    refreshRepo.save(rt);

    return raw;
  }

  @Transactional
  public RefreshResult rotate(String rawRefreshToken) {

    Instant now = Instant.now();
    String oldHash = sha256Hex(rawRefreshToken);

    RefreshToken old =
        refreshRepo
            .findByTokenHash(oldHash)
            .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

    if (!old.isActive(now)) {
      throw new BadCredentialsException("Refresh token expired or revoked");
    }
    User user = old.getUser();

    String newRaw = generateRawToken();
    String newHash = sha256Hex(newRaw);

    RefreshToken next = new RefreshToken();
    next.setUser(user);
    next.setTokenHash(newHash);
    next.setCreatedAt(now);
    next.setExpiresAt(now.plus(REFRESH_TTL));

    refreshRepo.save(next);

    old.setRevokedAt(now);
    old.setReplacedByHash(newHash);
    refreshRepo.save(old);

    Set<String> roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());

    return new RefreshResult(user.getEmail(), roles, newRaw);
  }

  @Transactional
  public void revoke(String rawRefreshToken) {
    String hash = sha256Hex(rawRefreshToken);

    System.out.println("REVOKE HASH = " + hash);

    refreshRepo.findByTokenHash(hash)
            .ifPresentOrElse(
                    rt -> {
                      System.out.println("TOKEN FOUND");
                      if (rt.getRevokedAt() == null) {
                        rt.setRevokedAt(Instant.now());
                        refreshRepo.save(rt);
                      }
                    },
                    () -> System.out.println("TOKEN NOT FOUND")
            );
  }

  private String generateRawToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String sha256Hex(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
