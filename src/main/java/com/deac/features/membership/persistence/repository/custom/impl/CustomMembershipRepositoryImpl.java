package com.deac.features.membership.persistence.repository.custom.impl;

import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.features.membership.persistence.repository.custom.CustomMembershipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.concurrent.Executor;

@Repository
@Transactional
public class CustomMembershipRepositoryImpl implements CustomMembershipRepository {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    public CustomMembershipRepositoryImpl(Executor databaseExecutor) {
    }

    @Override
    @Async("databaseExecutor")
    @Transactional
    public void updateInBatch(List<MembershipEntry> membershipEntries) {
        for (MembershipEntry membershipEntry : membershipEntries) {
            em.merge(membershipEntry);
        }
    }

}
