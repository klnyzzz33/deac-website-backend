package com.deac.features.news.controller;

import com.deac.exception.MyException;
import com.deac.features.news.dto.ModifyDto;
import com.deac.features.news.dto.NewsDto;
import com.deac.features.news.dto.NewsInfoDto;
import com.deac.features.news.service.NewsService;
import com.deac.response.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@RestController
public class NewsController {

    private final NewsService newsService;

    @Autowired
    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @PostMapping("/api/news/create")
    public ResponseMessage createNews(@Valid @RequestBody NewsDto newsDto) {
        Integer newsId = newsService.createNews(newsDto.getTitle(), newsDto.getDescription(), newsDto.getContent());
        return new ResponseMessage(newsId.toString());
    }

    @PostMapping("/api/news/delete")
    public ResponseMessage deleteNews(@RequestBody Integer newsId) {
        return new ResponseMessage(newsService.deleteNews(newsId));
    }

    @PostMapping("/api/news/update")
    public ResponseMessage updateNews(@RequestBody ModifyDto modifyDto) {
        return new ResponseMessage(newsService.updateNews(modifyDto));
    }

    @GetMapping("/api/news/list")
    public List<NewsInfoDto> listNews(@RequestParam(name = "pageNumber") int pageNumber,
                                      @RequestParam(name = "entriesPerPage") int entriesPerPage) {
        return newsService.listNews(pageNumber, entriesPerPage);
    }

    @GetMapping("/api/news/latest")
    public List<NewsInfoDto> listLatestNews(@RequestParam(name = "entriesPerPage") int entriesPerPage) {
        return newsService.getLatestNews(entriesPerPage);
    }

    @GetMapping("/api/news/latest_excluded")
    public List<NewsInfoDto> listLatestNewsWithExcluded(@RequestParam(name = "entriesPerPage") int entriesPerPage,
                                                        @RequestParam(name = "excludedId") int excludedId) {
        return newsService.getLatestNewsWithExcluded(entriesPerPage, excludedId);
    }

    @GetMapping("/api/news/count")
    public Long getNumberOfNews() {
        return newsService.getNumberOfNews();
    }

    @GetMapping("/api/news/open")
    public NewsInfoDto getSingleNews(@RequestParam(name = "id") Integer id) {
        return newsService.getSingleNews(id);
    }

}
