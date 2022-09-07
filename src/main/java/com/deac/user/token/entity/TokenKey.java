package com.deac.user.token.entity;

import lombok.*;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TokenKey implements Serializable {

    @Column(nullable = false)
    private Integer userId;

    @Column(length = 128, nullable = false, unique = true)
    private String token;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenKey tokenKey = (TokenKey) o;
        return userId.equals(tokenKey.getUserId()) && token.equals(tokenKey.getToken());
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, token);
    }

}
