package com.deac.user.persistence.repository;

import com.deac.user.persistence.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.deac.user.persistence.entity.User;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByRoles(@Param("roles") List<Role> roles);

    @Modifying
    @Transactional
    @Query(value = "UPDATE User u SET u.isEnabled = false WHERE u.id IN :ids")
    void banUsers(List<Integer> ids);

}
