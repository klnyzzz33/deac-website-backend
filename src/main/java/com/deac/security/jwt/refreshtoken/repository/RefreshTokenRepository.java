package com.deac.security.jwt.refreshtoken.repository;

import com.deac.security.jwt.refreshtoken.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {

    @Modifying
    @Transactional
    @Query(value = "UPDATE RefreshToken r SET r.token = :newToken, r.expiresAt = :expiresAt WHERE r.token = :originalToken")
    void updateTokenAndExpiresAt(@Param("originalToken") String originalToken, @Param("newToken") String newToken, @Param("expiresAt") Long expiresAt);

    @Query(value = "SELECT r FROM RefreshToken r WHERE r.token = :token")
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM RefreshToken r WHERE r.token = :token")
    void deleteByToken(String token);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM RefreshToken r WHERE r.username = :username")
    void deleteByUser(String username);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM RefreshToken r WHERE r.username = :username AND r.loginId = :loginId")
    void deleteByUserAndLoginId(String username, long loginId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM RefreshToken r WHERE r.expiresAt < :currentTimeMillis")
    void deleteAllByExpiresAtBefore(@Param("currentTimeMillis") Long currentTimeMillis);

}
