package com.deac.features.news.persistence.repository;

import com.deac.features.news.persistence.entity.News;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface NewsRepository extends JpaRepository<News, Integer> {

    @EntityGraph(attributePaths = {"author", "modifyEntries", "modifyEntries.modifyAuthor"})
    Optional<News> findDistinctById(Integer id);

    List<News> findBy(Pageable pageable);

    List<News> findByAuthorId(Integer authorId, Pageable pageable);

    List<News> findByIdNot(int excludedId, Pageable pageable);

    List<News> findByAuthorIdAndIdNot(Integer authorId, int excludedId, Pageable pageable);

    List<News> findByAuthorIdNotAndIdNot(Integer authorId, int excludedId, Pageable pageable);

    List<News> findByCreateDateAfter(Date createDate, Pageable pageable);

    List<News> findByCreateDateBefore(Date createDate, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "modifyEntries", "modifyEntries.modifyAuthor"})
    List<News> findDistinctByIdIn(List<Integer> ids);

    long countByAuthorId(Integer authorId);

    @Query("SELECT count(n) FROM News n WHERE n.id IN :ids")
    Integer findExistingMatchingNewsCount(List<Integer> ids);

    @Modifying
    @Transactional
    @Query("DELETE FROM News n WHERE n.id IN :ids")
    void deleteInBatchByIds(List<Integer> ids);

}
