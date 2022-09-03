package com.deac.user.persistence.entity;

import org.springframework.security.core.GrantedAuthority;

public enum Role implements GrantedAuthority {

    ADMIN,
    CLIENT;

    public String getAuthority() {
        return name();
    }

}
