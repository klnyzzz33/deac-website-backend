package com.deac.features.mailinglist.persistence.repository;

import com.deac.features.mailinglist.persistence.entity.MailingListEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MailingListRepository extends JpaRepository<MailingListEntry, Integer> {

    boolean existsByEmail(String email);

    Optional<MailingListEntry> findByEmail(String email);

}
