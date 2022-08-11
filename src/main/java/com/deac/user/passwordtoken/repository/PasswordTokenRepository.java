package com.deac.user.passwordtoken.repository;

import com.deac.user.passwordtoken.entity.PasswordToken;
import com.deac.user.passwordtoken.entity.PasswordTokenKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface PasswordTokenRepository extends JpaRepository<PasswordToken, PasswordTokenKey> {

    @Query(value = "SELECT t FROM PasswordToken t WHERE t.tokenId.token = :token")
    PasswordToken findByToken(@Param("token") String token);

    @Query(value = "SELECT count(t)>0 FROM PasswordToken t WHERE t.tokenId.token = :token")
    boolean existsByToken(@Param("token") String token);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM PasswordToken t WHERE t.tokenId.userId = :userId")
    void deleteAllByUserId(@Param("userId") Integer userId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM PasswordToken t WHERE t.tokenId.token = :token")
    void deleteByToken(@Param("token") String token);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM PasswordToken t WHERE t.expiresAt < :currentTimeMillis")
    void deleteAllByExpiresAtBefore(@Param("currentTimeMillis") Long currentTimeMillis);

}
