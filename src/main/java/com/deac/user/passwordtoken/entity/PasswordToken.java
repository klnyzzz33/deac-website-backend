package com.deac.user.passwordtoken.entity;

import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PasswordToken {

    @EmbeddedId
    private PasswordTokenKey tokenId;

    @Column(nullable = false)
    private Long expiresAt;

}
