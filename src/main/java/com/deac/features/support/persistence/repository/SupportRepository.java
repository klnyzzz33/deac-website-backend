package com.deac.features.support.persistence.repository;

import com.deac.features.support.persistence.entity.Ticket;
import com.deac.user.persistence.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportRepository extends JpaRepository<Ticket, Integer> {

    Optional<Ticket> findByTitle(String title);

    List<Ticket> findBy(Pageable pageable);

    List<Ticket> findByClosed(Boolean closed, Pageable pageable);

    List<Ticket> findByIssuer(User issuer);

    List<Ticket> findByIssuer(User issuer, Pageable pageable);

    List<Ticket> findByIssuerIsNull(Pageable pageable);

    List<Ticket> findByIssuerAndClosed(User issuer, Boolean closed, Pageable pageable);

    Long countAllByClosed(boolean closed);

    Long countByIssuer(User issuer);

    Long countByIssuerIsNull();

    Long countAllByIssuerAndClosed(User issuer, boolean closed);

    Long countByViewed(boolean viewed);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM Ticket t WHERE t.updateDate < :timeInMillis AND t.closed = :closed")
    void deleteAllByUpdateDateBeforeAndClosed(@Param("timeInMillis") Long timeInMillis, @Param("closed") boolean closed);

}
