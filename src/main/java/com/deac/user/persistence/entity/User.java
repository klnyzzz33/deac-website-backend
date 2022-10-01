package com.deac.user.persistence.entity;

import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.features.support.persistence.entity.Ticket;
import lombok.*;

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

    @Column(nullable = false)
    private String surname;

    @Column(nullable = false)
    private String lastname;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<Role> roles;

    @Column(nullable = false)
    private boolean isVerified;

    @Column(nullable = false)
    private boolean isEnabled;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "membershipentry_id")
    @ToString.Exclude
    private MembershipEntry membershipEntry;

    @OneToMany(mappedBy = "issuer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Ticket> tickets = List.of();

    public User(String username, String email, String password, String surname, String lastname, List<Role> roles) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.surname = surname;
        this.lastname = lastname;
        this.roles = roles;
        this.isVerified = false;
        this.isEnabled = false;
    }

}
