package com.deac.user.token.entity;

import lombok.*;

import javax.persistence.*;
import java.io.Serializable;

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

}
