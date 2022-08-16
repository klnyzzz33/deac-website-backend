package com.deac.user.token.repository;

import com.deac.user.token.entity.Token;
import com.deac.user.token.entity.TokenKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TokenRepository extends JpaRepository<Token, TokenKey> {

    @Query(value = "SELECT t FROM Token t WHERE t.tokenId.token = :token")
    Token findByToken(@Param("token") String token);

    @Query(value = "SELECT count(t)>0 FROM Token t WHERE t.tokenId.token = :token")
    boolean existsByToken(@Param("token") String token);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM Token t WHERE t.tokenId.userId = :userId AND t.purpose = :purpose")
    void deleteAllByUserIdAndPurpose(@Param("userId") Integer userId, @Param("purpose") String purpose);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM Token t WHERE t.tokenId.token = :token")
    void deleteByToken(@Param("token") String token);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM Token t WHERE t.expiresAt < :currentTimeMillis AND t.purpose = :purpose")
    void deleteAllByExpiresAtBeforeAndPurpose(@Param("currentTimeMillis") Long currentTimeMillis, @Param("purpose") String purpose);

    @Query(value = "SELECT t FROM Token t WHERE t.expiresAt < :currentTimeMillis AND t.purpose = :purpose")
    List<Token> findAllByExpiresAtBeforeAndPurpose(@Param("currentTimeMillis") Long currentTimeMillis, @Param("purpose") String purpose);

}
