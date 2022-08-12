package com.deac.user.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.deac.user.persistence.entity.User;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    User findByUsername(String username);

    User findByEmail(String email);

    boolean existsByRoles(@Param("roles") List<User.Role> roles);

}
