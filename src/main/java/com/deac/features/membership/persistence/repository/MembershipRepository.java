package com.deac.features.membership.persistence.repository;

import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.features.membership.persistence.repository.custom.CustomMembershipRepository;
import com.deac.user.persistence.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipRepository extends JpaRepository<MembershipEntry, User>, CustomMembershipRepository {

    @Query("SELECT DISTINCT m FROM MembershipEntry m JOIN FETCH m.user JOIN FETCH m.user.roles LEFT JOIN FETCH m.monthlyTransactions " +
            "WHERE m.user.isEnabled = true")
    List<MembershipEntry> findAllActiveMemberships();

    @Query("SELECT DISTINCT m FROM MembershipEntry m JOIN FETCH m.user JOIN FETCH m.user.roles LEFT JOIN FETCH m.monthlyTransactions " +
            "WHERE m.hasPaidMembershipFee = false AND m.user.isEnabled = true")
    List<MembershipEntry> findAllUnpaidMemberships();

    @Query("SELECT m FROM MembershipEntry m JOIN FETCH m.user WHERE m.user.username = :username")
    Optional<MembershipEntry> findByUsername(@Param("username") String username);

    @Query("SELECT DISTINCT m FROM MembershipEntry m JOIN FETCH m.user JOIN FETCH m.user.roles LEFT JOIN FETCH m.monthlyTransactions WHERE m.user.username = :username")
    Optional<MembershipEntry> findByUsernameFetch(@Param("username") String username);

    @Query("SELECT DISTINCT m FROM MembershipEntry m JOIN FETCH m.user JOIN FETCH m.user.roles LEFT JOIN FETCH m.monthlyTransactions WHERE m.user.email = :email")
    Optional<MembershipEntry> findByEmailFetch(@Param("email") String email);

    @EntityGraph(attributePaths = {"user"})
    List<MembershipEntry> findBy(Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    List<MembershipEntry> findByHasPaidMembershipFee(boolean hasPaidMembershipFee, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.roles", "monthlyTransactions"})
    List<MembershipEntry> findDistinctByIdIn(List<Integer> ids);

    Long countAllByHasPaidMembershipFee(boolean hasPaidMembershipFee);

}
