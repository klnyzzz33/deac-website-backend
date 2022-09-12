package com.deac.features.news.service.impl;

import com.deac.features.news.dto.ModifyDto;
import com.deac.features.news.dto.ModifyInfoDto;
import com.deac.features.news.dto.NewsInfoDto;
import com.deac.features.news.persistance.entity.ModifyEntry;
import com.deac.features.news.persistance.entity.News;
import com.deac.features.news.persistance.repository.NewsRepository;
import com.deac.features.news.service.NewsService;
import com.deac.exception.MyException;
import com.deac.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class NewsServiceImpl implements NewsService {

    private final NewsRepository newsRepository;

    private final UserService userService;

    @Autowired
    public NewsServiceImpl(NewsRepository newsRepository, UserService userService) {
        this.newsRepository = newsRepository;
        this.userService = userService;
    }

    @Override
    public Integer createNews(String title, String description, String content, String indexImageUrl) {
        if (indexImageUrl == null || indexImageUrl.isEmpty()) {
            indexImageUrl = "http://localhost/img/news-icon.png";
        }
        News news = new News(title, description, content, indexImageUrl, userService.getCurrentUserId(), new Date());
        newsRepository.save(news);
        return news.getId();
    }

    @Override
    public String deleteNews(Integer newsId) {
        if (!newsRepository.existsById(newsId)) {
            throw new MyException("News does not exist", HttpStatus.BAD_REQUEST);
        }
        newsRepository.deleteById(newsId);
        return "Successfully deleted news";
    }

    @Override
    public String deleteSelectedNews(@RequestBody List<Integer> newsIds) {
        if (newsIds.isEmpty()) {
            return "Successfully deleted news";
        }
        if (newsRepository.findExistingMatchingNewsCount(newsIds) != newsIds.size()) {
            throw new MyException("One or more news do not exist", HttpStatus.BAD_REQUEST);
        }
        newsRepository.deleteInBatchByIds(newsIds);
        return "Successfully deleted news";
    }

    @Override
    public String updateNews(ModifyDto modifyDto) {
        Optional<News> newsOptional = newsRepository.findById(modifyDto.getNewsId());
        if (newsOptional.isEmpty()) {
            throw new MyException("News does not exist", HttpStatus.BAD_REQUEST);
        }
        News news = newsOptional.get();
        news.setTitle(modifyDto.getTitle());
        news.setDescription(modifyDto.getDescription());
        news.setContent(modifyDto.getContent());
        String indexImageUrl = modifyDto.getIndexImageUrl();
        if (indexImageUrl == null || indexImageUrl.isEmpty()) {
            indexImageUrl = "http://localhost/img/news-icon.png";
        }
        news.setIndexImageUrl(indexImageUrl);
        ModifyEntry modifyEntry = new ModifyEntry(new Date(), userService.getCurrentUserId());
        news.getModifyEntries().add(modifyEntry);
        newsRepository.save(news);
        return "Successfully updated news";
    }

    @Override
    public List<NewsInfoDto> listNews(int pageNumber, int pageSize) {
        Pageable sortedByCreateDateDesc = PageRequest.of(pageNumber - 1, pageSize, Sort.by("createDate").descending());
        List<News> newsList = newsRepository.findBy(sortedByCreateDateDesc);
        return newsListToNewsInfoDtoList(newsList);
    }

    @Override
    public List<NewsInfoDto> getLatestNews(int pageSize) {
        Pageable sortedByCreateDateDesc = PageRequest.of(0, pageSize, Sort.by("createDate").descending());
        List<News> newsList = newsRepository.findBy(sortedByCreateDateDesc);
        return newsListToNewsInfoDtoList(newsList);
    }

    @Override
    public List<NewsInfoDto> getLatestNewsWithExcluded(int pageSize, int excludedId) {
        Pageable sortedByCreateDateDesc = PageRequest.of(0, pageSize, Sort.by("createDate").descending());
        List<News> newsList = newsRepository.findByIdNot(excludedId, sortedByCreateDateDesc);
        return newsListToNewsInfoDtoList(newsList);

    }

    private List<NewsInfoDto> newsListToNewsInfoDtoList(List<News> newsList) {
        return newsList
                .stream()
                .map(news -> {
                    ModifyEntry latestModifyEntry = news.getModifyEntries().stream().max(Comparator.comparing(ModifyEntry::getModifyDate)).orElse(null);
                    return new NewsInfoDto(news.getId(),
                            news.getTitle(),
                            news.getDescription(),
                            null,
                            news.getIndexImageUrl(),
                            userService.getUser(news.getAuthorId()),
                            news.getCreateDate(),
                            latestModifyEntry != null ? new ModifyInfoDto(latestModifyEntry.getModifyDate(), userService.getUser(latestModifyEntry.getModifyAuthorId())) : null);
                })
                .collect(Collectors.toList());
    }

    @Override
    public long getNumberOfNews() {
        return newsRepository.count();
    }

    @Override
    public NewsInfoDto getSingleNews(Integer id) {
        Optional<News> newsOptional = newsRepository.findById(id);
        if (newsOptional.isEmpty()) {
            throw new MyException("News does not exist", HttpStatus.BAD_REQUEST);
        }
        News news = newsOptional.get();
        ModifyEntry latestModifyEntry = news.getModifyEntries().stream().max(Comparator.comparing(ModifyEntry::getModifyDate)).orElse(null);
        return new NewsInfoDto(news.getId(),
                news.getTitle(),
                news.getDescription(),
                news.getContent(),
                news.getIndexImageUrl(),
                userService.getUser(news.getAuthorId()),
                news.getCreateDate(),
                latestModifyEntry != null ? new ModifyInfoDto(latestModifyEntry.getModifyDate(), userService.getUser(latestModifyEntry.getModifyAuthorId())) : null);
    }

}
