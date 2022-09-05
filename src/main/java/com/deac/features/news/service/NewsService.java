package com.deac.features.news.service;

import com.deac.features.news.dto.ModifyDto;
import com.deac.features.news.dto.NewsInfoDto;

import java.util.List;

public interface NewsService {

    Integer createNews(String title, String description, String content);

    String deleteNews(Integer newId);

    String updateNews(ModifyDto modifyDto);

    List<NewsInfoDto> listNews(int pageNumber, int pageSize);

    List<NewsInfoDto> getLatestNews(int pageSize);

    List<NewsInfoDto> getLatestNewsWithExcluded(int pageSize, int excludedId);

    long getNumberOfNews();

    NewsInfoDto getSingleNews(Integer id);

}
