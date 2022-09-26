package com.deac.features.membership.dto;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {

    private String fullName;

    private String username;

    private String email;

    private Date memberSince;

    private boolean enabled;

    private boolean verified;

    private boolean hasPaidMembershipFee;

    private boolean approved;

}
