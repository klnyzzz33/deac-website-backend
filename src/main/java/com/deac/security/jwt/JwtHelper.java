package com.deac.security.jwt;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Math.min;

@Component
public class JwtHelper {

    private static final SecureRandom random = new SecureRandom();

    private static final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    private final String secretKey;

    @Autowired
    public JwtHelper() {
        secretKey = Base64.getEncoder().encodeToString(generateRandomString(32).getBytes());
    }

    public String generateRandomString(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }

    public String createToken(String username, String type, Collection<? extends GrantedAuthority> roles, long tokenValidity) {
        Claims claims = createClaims(username, type, roles);
        Date now = new Date();
        Date validity = new Date(now.getTime() + tokenValidity);
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    public String createToken(String username, String type, Collection<? extends GrantedAuthority> roles, long loginIdentifier, Date absoluteExpirationTime, long tokenValidity) {
        Claims claims = createClaims(username, type, roles);
        claims.put("absexp", absoluteExpirationTime);
        claims.put("loginid", loginIdentifier);
        Date now = new Date();
        Date validity = new Date(now.getTime() + min(absoluteExpirationTime.getTime() - now.getTime(), tokenValidity));
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    private Claims createClaims(String username, String type, Collection<? extends GrantedAuthority> roles) {
        Claims claims = Jwts.claims().setSubject(username);
        claims.put("roles", roles.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
        claims.put("type", type);
        return claims;
    }

    public Claims parseJwtClaims(String token) {
        return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody();
    }

    public String getUsernameFromToken(String token) {
        return parseJwtClaims(token).getSubject();
    }

    public Collection<?> getRolesFromToken(String token) {
        return (Collection<?>) parseJwtClaims(token).get("roles");
    }

    public String getTypeFromToken(String token) {
        return (String) parseJwtClaims(token).get("type");
    }

    public long getLoginIdentifierFromToken(String token) {
        return (long) parseJwtClaims(token).get("loginid");
    }

    public Date getAbsoluteExpirationTimeFromToken(String token) {
        long value = (long) parseJwtClaims(token).get("absexp");
        return new Date(value);
    }

    public void authorize(String token) {
        String username = getUsernameFromToken(token);
        List<SimpleGrantedAuthority> roles = getRolesFromToken(token)
                .stream().map(role -> new SimpleGrantedAuthority(role.toString())).collect(Collectors.toList());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(username, null, roles);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    public void removeCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = setCookie("access-token", "", 0, true, "/");
        ResponseCookie refreshCookie = setCookie("refresh-token", "", 0, true, "/api/user/refresh");
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    public ResponseCookie setCookie(String name, String value, long age, boolean httpOnly, String path) {
        return ResponseCookie
                .from(name, value)
                .maxAge(age)
                .httpOnly(httpOnly)
                .sameSite("Strict")
                .secure(false)
                .path(path)
                .build();
    }

}
