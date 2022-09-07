package com.deac.security;

import com.deac.security.jwt.accesstoken.AccessTokenFilter;
import com.deac.security.jwt.accesstoken.AccessTokenProvider;
import com.deac.security.jwt.refreshtoken.RefreshTokenFilter;
import com.deac.security.jwt.refreshtoken.RefreshTokenProvider;
import com.deac.user.persistence.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

@Component
public class FilterConfigurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

    private final UserRepository userRepository;

    private final AccessTokenProvider accessTokenProvider;

    private final RefreshTokenProvider refreshTokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Autowired
    public FilterConfigurer(UserRepository userRepository, AccessTokenProvider accessTokenProvider, RefreshTokenProvider refreshTokenProvider) {
        this.userRepository = userRepository;
        this.accessTokenProvider = accessTokenProvider;
        this.refreshTokenProvider = refreshTokenProvider;
    }

    @Override
    public void configure(HttpSecurity http) {
        PrivilegesFilter privilegesFilter = new PrivilegesFilter(objectMapper, antPathMatcher);
        RefreshTokenFilter refreshTokenFilter = new RefreshTokenFilter(userRepository, refreshTokenProvider, objectMapper, antPathMatcher);
        AccessTokenFilter accessTokenFilter = new AccessTokenFilter(accessTokenProvider, objectMapper, antPathMatcher);
        http.addFilterBefore(privilegesFilter, FilterSecurityInterceptor.class);
        http.addFilterBefore(refreshTokenFilter, PrivilegesFilter.class);
        http.addFilterBefore(accessTokenFilter, RefreshTokenFilter.class);
    }

}
