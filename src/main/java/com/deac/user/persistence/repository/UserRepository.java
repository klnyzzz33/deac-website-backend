package com.deac.user.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.deac.user.persistence.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    User findByUsername(String username);

}
