package com.deac.features.membership.persistence.repository.custom;

import com.deac.features.membership.persistence.entity.MembershipEntry;

import java.util.List;

public interface CustomMembershipRepository {

    void updateInBatch(List<MembershipEntry> membershipEntries);

}
