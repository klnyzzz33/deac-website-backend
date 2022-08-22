package com.deac.security;

import com.deac.user.persistence.entity.User;
import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    private String secretKey = generateRandomSecretKey(10);

    private final long accessTokenValidityInMilliseconds = /*300000*/5000;

    private final long refreshTokenValidityInMilliseconds = 604800000;

    @Autowired
    public JwtTokenProvider() {
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

    public String createToken(String username, List<User.Role> roles, String type) {
        Claims claims = Jwts.claims().setSubject(username);
        claims.put("auth", roles.stream().map(role -> new SimpleGrantedAuthority(role.getAuthority())).collect(Collectors.toList()));
        claims.put("type", type);
        Date now = new Date();
        Date validity = new Date(now.getTime() + (type.equals("access-token") ? accessTokenValidityInMilliseconds : refreshTokenValidityInMilliseconds));
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

}
