package com.deac.features.membership.service;

import com.deac.features.membership.dto.MembershipEntryInfoDto;

import java.util.List;

public interface MembershipService {

    String toggleUserEnabled(String username, boolean isEnabled);

    String toggleHasPaidMembershipFee(String username, boolean hasPaidMembershipFee);

    List<MembershipEntryInfoDto> listMembershipEntries(int pageNumber, int pageSize);

    long getNumberOfMemberships();

}
