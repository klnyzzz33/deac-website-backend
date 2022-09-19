package com.deac.features.membership.dto;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDto {

    private String username;

    private String email;

    private Date memberSince;

    private boolean hasPaidMembershipFee;

    private String monthlyTransactionReceiptPath;

    private boolean approved;

}
