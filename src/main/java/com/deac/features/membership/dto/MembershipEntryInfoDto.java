package com.deac.features.membership.dto;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MembershipEntryInfoDto {

    private String username;

    private Date memberSince;

    private boolean hasPaidMembershipFee;

    private boolean isEnabled;

    private boolean approved;

}
