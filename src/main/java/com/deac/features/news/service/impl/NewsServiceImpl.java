package com.deac.features.news.service.impl;

import com.deac.features.news.persistance.entity.News;
import com.deac.features.news.persistance.repository.NewsRepository;
import com.deac.features.news.service.NewsService;
import com.deac.user.exception.MyException;
import com.deac.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Date;

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
            News news = new News(title, content, userService.getCurrentUsername(), new Date());
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

}
