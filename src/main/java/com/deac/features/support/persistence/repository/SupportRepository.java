package com.deac.features.support.persistence.repository;

import com.deac.features.support.persistence.entity.Ticket;
import com.deac.user.persistence.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportRepository extends JpaRepository<Ticket, Integer> {

    Optional<Ticket> findByTitle(String title);

    List<Ticket> findBy(Pageable pageable);

    List<Ticket> findByClosed(Boolean closed, Pageable pageable);

    List<Ticket> findByIssuer(User issuer, Pageable pageable);

    List<Ticket> findByIssuerIsNull(Pageable pageable);

    List<Ticket> findByIssuerAndClosed(User issuer, Boolean closed, Pageable pageable);

    Long countAllByClosed(boolean closed);

    Long countByIssuer(User issuer);

    Long countByIssuerIsNull();

    Long countAllByIssuerAndClosed(User issuer, boolean closed);

}
