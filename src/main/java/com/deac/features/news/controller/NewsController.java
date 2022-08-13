package com.deac.features.news.controller;

import com.deac.features.news.model.ModifyDto;
import com.deac.features.news.model.NewsDto;
import com.deac.features.news.model.NewsInfoDto;
import com.deac.features.news.service.NewsService;
import com.deac.user.model.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

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
    public List<NewsInfoDto> listNews() {
        return newsService.listNews();
    }

}
