package com.deac.features.support.persistence.repository;

import com.deac.features.support.persistence.entity.Ticket;
import com.deac.user.persistence.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    @EntityGraph(attributePaths = {"issuer", "issuer.roles"})
    Optional<Ticket> findByTitle(String title);

    @EntityGraph(attributePaths = {"issuer", "issuer.roles"})
    Optional<Ticket> findById(Integer id);

    @Query("SELECT DISTINCT t FROM Ticket t LEFT JOIN FETCH t.attachmentPaths WHERE t.id = :id")
    Ticket findByIdFetchAttachments(Integer id);

    @EntityGraph(attributePaths = {"comments.issuer"})
    @Query("SELECT DISTINCT t FROM Ticket t LEFT JOIN FETCH t.comments WHERE t.id = :id")
    Ticket findByIdFetchComments(Integer id);

    List<Ticket> findBy(Pageable pageable);

    List<Ticket> findByIssuerIsNull(Pageable pageable);

    List<Ticket> findByClosed(Boolean closed, Pageable pageable);

    @EntityGraph(attributePaths = {"issuer", "issuer.roles"})
    List<Ticket> findByIssuer(User issuer);

    List<Ticket> findByIssuer(User issuer, Pageable pageable);

    List<Ticket> findByIssuerAndClosed(User issuer, Boolean closed, Pageable pageable);

    @Query("SELECT DISTINCT t FROM Ticket t JOIN FETCH t.issuer JOIN FETCH t.issuer.roles WHERE t.id IN :ids")
    List<Ticket> fetchIssuerAndRoles(List<Integer> ids);

    @Query("SELECT DISTINCT t FROM Ticket t LEFT JOIN FETCH t.attachmentPaths WHERE t.id IN :ids")
    List<Ticket> fetchAttachments(List<Integer> ids);

    @EntityGraph(attributePaths = {"comments.issuer"})
    @Query("SELECT DISTINCT t FROM Ticket t LEFT JOIN FETCH t.comments WHERE t.id IN :ids")
    List<Ticket> fetchComments(List<Integer> ids);

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
