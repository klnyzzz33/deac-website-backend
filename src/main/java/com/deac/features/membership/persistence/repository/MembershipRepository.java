package com.deac.features.membership.persistence.repository;

import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.user.persistence.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<MembershipEntry, User> {

    @Query("SELECT n FROM MembershipEntry n WHERE n.user.username = :username")
    Optional<MembershipEntry> findByUsername(@Param("username") String username);

    List<MembershipEntry> findBy(Pageable pageable);

}
