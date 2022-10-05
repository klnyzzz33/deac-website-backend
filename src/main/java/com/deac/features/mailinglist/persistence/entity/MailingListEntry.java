package com.deac.features.mailinglist.persistence.entity;

import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MailingListEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Integer id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String tokenValue;

    public MailingListEntry(String email) {
        this.email = email;
    }

}
