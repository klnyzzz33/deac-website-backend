package com.deac.features.news.service;

import com.deac.features.news.model.ModifyDto;

public interface NewsService {

    Integer createNews(String title, String content);

    String deleteNews(Integer newId);

    String updateNews(ModifyDto modifyDto);

}
