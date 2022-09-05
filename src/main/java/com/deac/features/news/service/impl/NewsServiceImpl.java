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
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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
    public Integer createNews(String title, String description, String content) {
        try {
            if (!userService.hasAdminPrivileges()) {
                throw new MyException("Could not create news, you don't have admin privileges", HttpStatus.UNAUTHORIZED);
            }
            News news = new News(title, description, content, userService.getCurrentUserId(userService.getCurrentUsername()), new Date());
            newsRepository.save(news);
            return news.getId();
        } catch (DataAccessException e) {
            throw new MyException("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String deleteNews(Integer newsId) {
        try {
            if (!userService.hasAdminPrivileges()) {
                throw new MyException("Could not create news, you don't have admin privileges", HttpStatus.UNAUTHORIZED);
            }
            if (!newsRepository.existsById(newsId)) {
                throw new MyException("News does not exist", HttpStatus.BAD_REQUEST);
            }
            newsRepository.deleteById(newsId);
            return "Successfully deleted news";
        } catch (DataAccessException e) {
            throw new MyException("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String updateNews(ModifyDto modifyDto) {
        try {
            if (!userService.hasAdminPrivileges()) {
                throw new MyException("Could not create news, you don't have admin privileges", HttpStatus.UNAUTHORIZED);
            }
            Optional<News> newsOptional = newsRepository.findById(modifyDto.getNewsId());
            if (newsOptional.isEmpty()) {
                throw new MyException("News does not exist", HttpStatus.BAD_REQUEST);
            }
            News news = newsOptional.get();
            news.setTitle(modifyDto.getTitle());
            news.setDescription(modifyDto.getDescription());
            news.setContent(modifyDto.getContent());
            ModifyEntry modifyEntry = new ModifyEntry(new Date(), userService.getCurrentUserId(userService.getCurrentUsername()));
            news.getModifyEntries().add(modifyEntry);
            newsRepository.save(news);
            return "Successfully updated news";
        } catch (DataAccessException e) {
            throw new MyException("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public List<NewsInfoDto> listNews(int pageNumber, int pageSize) {
        try {
            Pageable sortedByCreateDateDesc = PageRequest.of(pageNumber - 1, pageSize, Sort.by("createDate").descending());
            List<News> newsList = newsRepository.findBy(sortedByCreateDateDesc);
            return newsListToNewsInfoDtoList(newsList);
        } catch (DataAccessException e) {
            throw new MyException("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public List<NewsInfoDto> getLatestNews(int pageSize) {
        try {
            Pageable sortedByCreateDateDesc = PageRequest.of(0, pageSize, Sort.by("createDate").descending());
            List<News> newsList = newsRepository.findBy(sortedByCreateDateDesc);
            return newsListToNewsInfoDtoList(newsList);
        } catch (DataAccessException e) {
            throw new MyException("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public List<NewsInfoDto> getLatestNewsWithExcluded(int pageSize, int excludedId) {
        try {
            Pageable sortedByCreateDateDesc = PageRequest.of(0, pageSize, Sort.by("createDate").descending());
            System.out.println(excludedId);
            List<News> newsList = newsRepository.findByIdNot(excludedId, sortedByCreateDateDesc);
            return newsListToNewsInfoDtoList(newsList);
        } catch (DataAccessException e) {
            throw new MyException("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
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
                            userService.getUser(news.getAuthorId()),
                            news.getCreateDate(),
                            latestModifyEntry != null ? new ModifyInfoDto(latestModifyEntry.getModifyDate(), userService.getUser(latestModifyEntry.getModifyAuthorId())) : null);
                })
                .collect(Collectors.toList());
    }

    @Override
    public long getNumberOfNews() {
        try {
            return newsRepository.count();
        } catch (DataAccessException e) {
            throw new MyException("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public NewsInfoDto getSingleNews(Integer id) {
        try {
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
                    userService.getUser(news.getAuthorId()),
                    news.getCreateDate(),
                    latestModifyEntry != null ? new ModifyInfoDto(latestModifyEntry.getModifyDate(), userService.getUser(latestModifyEntry.getModifyAuthorId())) : null);
        } catch (DataAccessException e) {
            throw new MyException("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
