package com.deac.features.membership.persistence.repository;

import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.user.persistence.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<MembershipEntry, User> {

    @Override
    @EntityGraph(attributePaths = {"user", "monthlyTransactions"})
    List<MembershipEntry> findAll();

    @Query("SELECT n FROM MembershipEntry n WHERE n.user.username = :username")
    Optional<MembershipEntry> findByUsername(@Param("username") String username);

    @Query("SELECT n FROM MembershipEntry n WHERE n.user.email = :email")
    Optional<MembershipEntry> findByEmail(@Param("email") String email);

    List<MembershipEntry> findBy(Pageable pageable);

    List<MembershipEntry> findByHasPaidMembershipFee(boolean hasPaidMembershipFee, Pageable pageable);

    Long countAllByHasPaidMembershipFee(boolean hasPaidMembershipFee);

}
