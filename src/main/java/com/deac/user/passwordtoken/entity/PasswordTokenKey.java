package com.deac.user.passwordtoken.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordTokenKey implements Serializable {

    @Column(nullable = false)
    private Integer userId;

    @Column(length = 128, nullable = false, unique = true)
    private String token;

}
