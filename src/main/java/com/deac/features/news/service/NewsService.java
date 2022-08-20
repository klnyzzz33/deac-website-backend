package com.deac.features.news.service;

import com.deac.features.news.model.ModifyDto;
import com.deac.features.news.model.NewsInfoDto;

import java.util.List;

public interface NewsService {

    Integer createNews(String title, String description, String content);

    String deleteNews(Integer newId);

    String updateNews(ModifyDto modifyDto);

    List<NewsInfoDto> listNews(long min, long max);

    long getNumberOfNews();

    NewsInfoDto getSingleNews(Integer id);

}
