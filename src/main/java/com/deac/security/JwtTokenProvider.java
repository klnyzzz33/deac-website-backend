package com.deac.security;

import com.deac.user.persistence.entity.Role;
import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.min;

@Component
public class JwtTokenProvider {

    private String secretKey = generateRandomSecretKey(10);

    private final long accessTokenValidityInMilliseconds;

    private final long refreshTokenSlidingValidityInMilliseconds;

    private final long refreshTokenAbsoluteValidityInMilliseconds;

    @Autowired
    public JwtTokenProvider(Environment environment) {
        accessTokenValidityInMilliseconds = Objects.requireNonNull(environment.getProperty("jwt.access.validity", Long.class)) * 1000;
        refreshTokenSlidingValidityInMilliseconds = Objects.requireNonNull(environment.getProperty("jwt.refresh.sliding.validity", Long.class)) * 1000;
        refreshTokenAbsoluteValidityInMilliseconds = Objects.requireNonNull(environment.getProperty("jwt.refresh.absolute.validity", Long.class)) * 1000;
        secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
    }

    private static String generateRandomSecretKey(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append(chars.charAt(random.nextInt(chars.length())));
        }
        return builder.toString();
    }

    public long getRefreshTokenAbsoluteValidityInMilliseconds() {
        return refreshTokenAbsoluteValidityInMilliseconds;
    }

    public String createToken(String username, List<Role> roles, String type, Date absoluteExpirationTime) {
        Claims claims = Jwts.claims().setSubject(username);
        claims.put("auth", roles.stream().map(role -> new SimpleGrantedAuthority(role.getAuthority())).collect(Collectors.toList()));
        claims.put("type", type);
        if (absoluteExpirationTime != null) {
            claims.put("absexp", absoluteExpirationTime);
        }
        Date now = new Date();
        Date validity = new Date(now.getTime() + (type.equals("access-token")
                ? accessTokenValidityInMilliseconds
                : min(Objects.requireNonNull(absoluteExpirationTime).getTime() - now.getTime(), refreshTokenSlidingValidityInMilliseconds)));
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    public boolean validateToken(String token) {
        Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
        return true;
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody().getSubject();
    }

    public String getTypeFromToken(String token) {
        return (String) Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody().get("type");
    }

    public Date getAbsoluteExpirationTimeFromToken(String token) {
        long value = (long) Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody().get("absexp");
        return new Date(value);
    }

}
