package com.deac.features.membership.service;

import com.deac.features.membership.dto.MembershipEntryInfoDto;
import com.deac.features.membership.dto.MonthlyTransactionDto;
import com.deac.features.membership.dto.ProfileDto;
import com.deac.features.membership.dto.UserProfileDto;

import java.util.List;

public interface MembershipService {

    String toggleUserEnabled(String username, boolean isEnabled);

    String toggleApproved(String username, boolean isApproved);

    String toggleHasPaidMembershipFee(String username, boolean hasPaidMembershipFee);

    List<MembershipEntryInfoDto> listMembershipEntries(int pageNumber, int pageSize);

    long getNumberOfMemberships();

    UserProfileDto getUserProfileData(String username);

    List<MonthlyTransactionDto> listUserTransactions(String username);

    byte[] downloadUserReceipt(String username, String receiptPath);

    ProfileDto getCurrentUserProfileData();

    List<MonthlyTransactionDto> listCurrentUserTransactions();

    byte[] downloadCurrentUserReceipt(String receiptPath);
}
