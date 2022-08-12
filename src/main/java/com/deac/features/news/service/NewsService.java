package com.deac.features.news.service;

public interface NewsService {

    Integer createNews(String title, String content);

    String deleteNews(Integer newId);

}
