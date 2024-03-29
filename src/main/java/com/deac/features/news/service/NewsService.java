package com.deac.features.news.service;

import com.deac.features.news.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface NewsService {

    String uploadImage(MultipartFile file);

    Integer createNews(String title, String description, String content, String indexImageUrl);

    String deleteNews(Integer newId);

    String deleteSelectedNews(List<Integer> newsIds);

    String updateNews(ModifyDto modifyDto);

    List<NewsInfoDto> listNews(int pageNumber, int pageSize);

    List<NewsInfoDto> getLatestNews(int pageSize);

    List<NewsInfoDto> getLatestNewsWithExcluded(int pageSize, int excludedId);

    List<NewsInfoDto> getMostPopularNewsByAuthorWithExcluded(String author, int pageSize, int excludedId);

    List<NewsInfoDto> listNewsByAuthor(String author, int pageNumber, int entriesPerPage);

    List<NewsInfoDto> getLatestMostPopularNews(int pageSize);

    long getNumberOfNews();

    long getNumberOfNewsByAuthor(String author);

    NewsInfoDto getSingleNews(Integer id);

    NewsSearchBarDto getTopSearchResults(String searchTerm, int pageSize);

    NewsSearchListDto searchNews(String searchTerm, int pageNumber, int pageSize);
}
