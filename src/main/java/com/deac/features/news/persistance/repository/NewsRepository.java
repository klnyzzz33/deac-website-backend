package com.deac.features.news.persistance.repository;

import com.deac.features.news.persistance.entity.News;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewsRepository extends JpaRepository<News, Integer> {

    List<News> findBy(Pageable pageable);

    List<News> findByIdNot(int excludedId, Pageable pageable);

}
