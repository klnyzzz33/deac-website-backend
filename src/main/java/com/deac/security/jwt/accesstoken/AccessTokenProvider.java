package com.deac.security.jwt.accesstoken;

import com.deac.exception.MyException;
import com.deac.security.jwt.JwtHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AccessTokenProvider {

    private final JwtHelper jwtHelper;

    private final long accessTokenValidityInMilliseconds;

    @Autowired
    public AccessTokenProvider(JwtHelper jwtHelper, Environment environment) {
        this.jwtHelper = jwtHelper;
        accessTokenValidityInMilliseconds = Objects.requireNonNull(environment.getProperty("jwt.access.validity", Long.class)) * 1000;
    }

    public String createToken(String username, String type, Collection<? extends GrantedAuthority> roles) {
        return jwtHelper.createToken(username, type, roles, accessTokenValidityInMilliseconds);
    }

    public void validateToken(String token) {
        if (!jwtHelper.getTypeFromToken(token).equals("access-token")) {
            throw new MyException("Invalid access token", HttpStatus.UNAUTHORIZED);
        }
    }

    public void authorize(String accessToken) {
        jwtHelper.authorize(accessToken);
    }

}
