package com.deac.features.membership.service;

import com.deac.features.membership.dto.MembershipEntryInfoDto;
import com.deac.features.membership.dto.ProfileDto;

import java.util.List;

public interface MembershipService {

    String toggleUserEnabled(String username, boolean isEnabled);

    String toggleApproved(String username, boolean isApproved);

    String toggleHasPaidMembershipFee(String username, boolean hasPaidMembershipFee);

    List<MembershipEntryInfoDto> listMembershipEntries(int pageNumber, int pageSize);

    long getNumberOfMemberships();

    ProfileDto getProfileData();

}
