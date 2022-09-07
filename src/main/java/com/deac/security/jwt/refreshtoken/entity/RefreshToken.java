package com.deac.security.jwt.refreshtoken.entity;

import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Integer id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private long loginId;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private Long expiresAt;

    public RefreshToken(String username, long loginId, String token, Long expiresAt) {
        this.username = username;
        this.loginId = loginId;
        this.token = token;
        this.expiresAt = expiresAt;
    }

}
