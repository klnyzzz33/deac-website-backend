package com.deac.user.token.entity;

import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Token {

    @EmbeddedId
    private TokenKey tokenId;

    @Column(nullable = false)
    private Long expiresAt;

    @Column(nullable = false)
    private String purpose;

}
