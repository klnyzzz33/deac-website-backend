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

    List<Ticket> findByIssuer(User issuer, Pageable pageable);

    Long countByIssuer(User issuer);

}