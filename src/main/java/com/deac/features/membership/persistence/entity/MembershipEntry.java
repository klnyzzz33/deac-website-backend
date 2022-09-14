package com.deac.features.membership.persistence.entity;

import com.deac.user.persistence.entity.User;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import javax.persistence.*;
import java.util.Date;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MembershipEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Integer id;

    @OneToOne(mappedBy = "membershipEntry")
    private User user;

    @DateTimeFormat(pattern = "yyyy.MM.dd")
    @Column(nullable = false)
    private Date memberSince = new Date();

    @Column(nullable = false)
    private boolean hasPaidMembershipFee = false;

    private String monthlyTransactionReceiptPath = null;

    @Column(nullable = false)
    private boolean approved = false;

}