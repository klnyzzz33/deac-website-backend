package com.deac.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;
import org.springframework.stereotype.Component;

@Component
public class FilterConfigurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

    private final JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper;

    public FilterConfigurer(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public void configure(HttpSecurity http) {
        PrivilegesFilter privilegesFilter =  new PrivilegesFilter(objectMapper);
        JwtTokenFilter jwtTokenFilter = new JwtTokenFilter(jwtTokenProvider, objectMapper);
        http.addFilterBefore(privilegesFilter, FilterSecurityInterceptor.class);
        http.addFilterBefore(jwtTokenFilter, PrivilegesFilter.class);
    }

}
