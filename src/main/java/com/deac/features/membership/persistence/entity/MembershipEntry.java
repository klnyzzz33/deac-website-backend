package com.deac.features.membership.persistence.entity;

import com.deac.user.persistence.entity.User;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import javax.persistence.*;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;

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

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @MapKeyColumn(length = 128)
    @ToString.Exclude
    private Map<String, MonthlyTransaction> monthlyTransactions = Map.of(YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy.MM")), new MonthlyTransaction(YearMonth.now(), null));

    @Column(nullable = false)
    private boolean approved = false;

    private String customerId = null;

    public MembershipEntry(boolean hasPaidMembershipFee, Map<String, MonthlyTransaction> monthlyTransactions, boolean approved) {
        this.hasPaidMembershipFee = hasPaidMembershipFee;
        this.monthlyTransactions = monthlyTransactions;
        this.approved = approved;
    }

}
