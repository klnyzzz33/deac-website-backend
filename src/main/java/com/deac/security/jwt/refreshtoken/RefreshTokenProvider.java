package com.deac.security.jwt.refreshtoken;

import com.deac.exception.MyException;
import com.deac.security.jwt.JwtHelper;
import com.deac.security.jwt.refreshtoken.entity.RefreshToken;
import com.deac.security.jwt.refreshtoken.repository.RefreshTokenRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.util.*;

@Component
public class RefreshTokenProvider {

    private final RefreshTokenRepository refreshTokenRepository;

    private final JwtHelper jwtHelper;

    private final long refreshTokenSlidingValidityInMilliseconds;

    private final long refreshTokenAbsoluteValidityInMilliseconds;

    @Autowired
    public RefreshTokenProvider(RefreshTokenRepository refreshTokenRepository, JwtHelper jwtHelper, Environment environment) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtHelper = jwtHelper;
        refreshTokenSlidingValidityInMilliseconds = Objects.requireNonNull(environment.getProperty("jwt.refresh.sliding.validity", Long.class)) * 1000;
        refreshTokenAbsoluteValidityInMilliseconds = Objects.requireNonNull(environment.getProperty("jwt.refresh.absolute.validity", Long.class)) * 1000;
    }

    public long getRefreshTokenAbsoluteValidityInMilliseconds() {
        return refreshTokenAbsoluteValidityInMilliseconds;
    }

    public String createToken(String originalToken, String username, String type, Collection<? extends GrantedAuthority> roles, long loginIdentifier, Date absoluteExpirationTime) {
        if (originalToken.isEmpty()) {
            return createNewToken(username, type, roles, loginIdentifier, absoluteExpirationTime);
        } else {
            return replaceExistingToken(originalToken, username, type, roles, loginIdentifier, absoluteExpirationTime);
        }
    }

    private String createNewToken(String username, String type, Collection<? extends GrantedAuthority> roles, long loginIdentifier, Date absoluteExpirationTime) {
        String token = jwtHelper.createToken(username, type, roles, loginIdentifier, absoluteExpirationTime, refreshTokenSlidingValidityInMilliseconds);
        Long expiresAt = System.currentTimeMillis() + refreshTokenSlidingValidityInMilliseconds;
        RefreshToken refreshToken = new RefreshToken(username, loginIdentifier, token, expiresAt);
        refreshTokenRepository.save(refreshToken);
        return token;
    }

    private String replaceExistingToken(String originalToken, String username, String type, Collection<? extends GrantedAuthority> roles, long loginIdentifier, Date absoluteExpirationTime) {
        try {
            validateToken(originalToken);
            return updateToken(originalToken, username, type, roles, loginIdentifier, absoluteExpirationTime);
        } catch (JwtException | IllegalArgumentException e) {
            return createNewToken(username, type, roles, loginIdentifier, absoluteExpirationTime);
        }
    }

    public String updateToken(String originalToken, String username, String type, Collection<? extends GrantedAuthority> roles, long loginIdentifier, Date absoluteExpirationTime) {
        String newToken = jwtHelper.createToken(username, type, roles, loginIdentifier, absoluteExpirationTime, refreshTokenSlidingValidityInMilliseconds);
        Long expiresAt = System.currentTimeMillis() + refreshTokenSlidingValidityInMilliseconds;
        refreshTokenRepository.updateTokenAndExpiresAt(originalToken, newToken, expiresAt);
        return newToken;
    }

    public String validateToken(String token) {
        if (!jwtHelper.getTypeFromToken(token).equals("refresh-token")) {
            throw new MyException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }
        Optional<RefreshToken> refreshTokenOptional = refreshTokenRepository.findByToken(token);
        if (refreshTokenOptional.isEmpty()) {
            throw new MyException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }
        RefreshToken refreshToken = refreshTokenOptional.get();
        Long expiresAt = refreshToken.getExpiresAt();
        if (System.currentTimeMillis() > expiresAt) {
            refreshTokenRepository.deleteByToken(token);
            throw new MyException("Expired refresh token", HttpStatus.UNAUTHORIZED);
        }
        return refreshToken.getUsername();
    }

    public void invalidateUserTokens(String username) {
        refreshTokenRepository.deleteByUser(username);
    }

    public void invalidateAllExpiredTokens() {
        refreshTokenRepository.deleteAllByExpiresAtBefore(System.currentTimeMillis());
    }

    public long getLoginIdentifierFromToken(String token) {
        return jwtHelper.getLoginIdentifierFromToken(token);
    }

    public Date getAbsoluteExpirationTimeFromToken(String token) {
        return jwtHelper.getAbsoluteExpirationTimeFromToken(token);
    }

    public void authorize(String refreshToken) {
        jwtHelper.authorize(refreshToken);
    }

    public void removeCookies(HttpServletResponse response) {
        jwtHelper.removeCookies(response);
    }

    public ResponseCookie setCookie(String name, String value, long age, boolean httpOnly, String path) {
        return jwtHelper.setCookie(name, value, age, httpOnly, path);
    }

}
