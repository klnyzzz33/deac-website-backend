package com.deac.user.persistence.entity;

import lombok.*;
import org.springframework.security.core.GrantedAuthority;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.List;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Integer id;

    @Size(min = 4, max = 255, message = "Minimum username length: 4 characters")
    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Size(min = 8, message = "Minimum password length: 8 characters")
    private String password;

    @ElementCollection(fetch = FetchType.EAGER)
    List<Role> roles;

    public User(String username, String email, String password, List<Role> roles) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.roles = roles;
    }

    public enum Role implements GrantedAuthority {

        ROLE_ADMIN,
        ROLE_CLIENT;

        public String getAuthority() {
            return name();
        }

    }

}
