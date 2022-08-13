package com.deac.features.news.service.impl;

import com.deac.features.news.model.ModifyDto;
import com.deac.features.news.model.ModifyInfoDto;
import com.deac.features.news.model.NewsInfoDto;
import com.deac.features.news.persistance.entity.ModifyEntry;
import com.deac.features.news.persistance.entity.News;
import com.deac.features.news.persistance.repository.NewsRepository;
import com.deac.features.news.service.NewsService;
import com.deac.user.exception.MyException;
import com.deac.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
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
    public Integer createNews(String title, String content) {
        try {
            if (!userService.hasAdminPrivileges()) {
                throw new MyException("Could not create news, you don't have admin privileges", HttpStatus.UNAUTHORIZED);
            }
            News news = new News(title, content, userService.getCurrentUserId(userService.getCurrentUsername()), new Date());
            newsRepository.save(news);
            return news.getId();
        } catch (DataAccessException e) {
            throw new MyException("Could not create news, internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
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
            throw new MyException("Could not delete news, internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
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
            news.setContent(modifyDto.getContent());
            ModifyEntry modifyEntry = new ModifyEntry(new Date(), userService.getCurrentUserId(userService.getCurrentUsername()));
            news.getModifyEntries().add(modifyEntry);
            newsRepository.save(news);
            return "Successfully updated news";
        } catch (DataAccessException e) {
            throw new MyException("Could not update news, internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public List<NewsInfoDto> listNews() {
        try {
            List<News> newsList = newsRepository.findAll();
            return newsList
                    .stream()
                    .map(news -> {
                        ModifyEntry latestModifyEntry = news.getModifyEntries().stream().max(Comparator.comparing(ModifyEntry::getModifyDate)).orElse(null);
                        return new NewsInfoDto(news.getId(),
                            news.getTitle(),
                            news.getContent(),
                            userService.getUser(news.getAuthorId()),
                            news.getCreateDate(),
                            latestModifyEntry != null ? new ModifyInfoDto(latestModifyEntry.getModifyDate(), userService.getUser(latestModifyEntry.getModifyAuthorId())) : null);
                    })
                    .collect(Collectors.toList());
        } catch (DataAccessException e) {
            throw new MyException("Could not list news, internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
