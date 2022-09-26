package com.deac.features.membership.dto;

import lombok.*;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDto {

    private String fullName;

    private String username;

    private String email;

    private Date memberSince;

    private boolean hasPaidMembershipFee;

    private boolean approved;

}
