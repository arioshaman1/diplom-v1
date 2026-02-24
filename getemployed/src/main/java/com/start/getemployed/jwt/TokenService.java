package com.start.getemployed.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

@Service
public class TokenService {
    private final JwtEncoder jwtEncoder;
    private final long ttlMinutes;

    public TokenService(JwtEncoder jwtEncoder,
                        @Value("${jwt.ttl-minutes:60}") long ttlMinutes) {
        this.jwtEncoder = jwtEncoder;
        this.ttlMinutes = ttlMinutes;
    }


    public String generateToken(Authentication auth) {
        Instant now = Instant.now();

        String scope = auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.joining(" "));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("self")
                .issuedAt(now)
                .expiresAt(now.plus(ttlMinutes, ChronoUnit.MINUTES))
                .subject(auth.getName())
                .claim("scope", scope)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}