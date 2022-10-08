package com.deac.features.news.controller;

import com.deac.features.news.dto.*;
import com.deac.features.news.service.NewsService;
import com.deac.response.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;

@RestController
public class NewsController {

    private final NewsService newsService;

    @Autowired
    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @PostMapping("/api/admin/news/upload_image")
    public ResponseMessage uploadImage(@RequestParam("indexImage") MultipartFile file) {
        return new ResponseMessage(newsService.uploadImage(file));
    }

    @PostMapping("/api/admin/news/create")
    public ResponseMessage createNews(@Valid @RequestBody NewsDto newsDto) {
        Integer newsId = newsService.createNews(newsDto.getTitle(),
                newsDto.getDescription(),
                newsDto.getContent(),
                newsDto.getIndexImageUrl());
        return new ResponseMessage(newsId.toString());
    }

    @PostMapping("/api/admin/news/delete")
    public ResponseMessage deleteNews(@RequestBody Integer newsId) {
        return new ResponseMessage(newsService.deleteNews(newsId));
    }

    @PostMapping("/api/admin/news/delete_selected")
    public ResponseMessage deleteSelectedNews(@RequestBody List<Integer> newsIds) {
        return new ResponseMessage(newsService.deleteSelectedNews(newsIds));
    }

    @PostMapping("/api/admin/news/update")
    public ResponseMessage updateNews(@Valid @RequestBody ModifyDto modifyDto) {
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

    @GetMapping("/api/news/list/author")
    public List<NewsInfoDto> listNewsByAuthor(@RequestParam(name = "author") String author,
                                              @RequestParam(name = "pageNumber") int pageNumber,
                                              @RequestParam(name = "entriesPerPage") int entriesPerPage) {
        return newsService.listNewsByAuthor(author, pageNumber, entriesPerPage);
    }

    @GetMapping("/api/news/count")
    public Long getNumberOfNews() {
        return newsService.getNumberOfNews();
    }

    @GetMapping("/api/news/count/author")
    public Long getNumberOfNewsByAuthor(@RequestParam(name = "author") String author) {
        return newsService.getNumberOfNewsByAuthor(author);
    }

    @GetMapping("/api/news/open")
    public NewsInfoDto getSingleNews(@RequestParam(name = "id") Integer id) {
        return newsService.getSingleNews(id);
    }

    @GetMapping("/api/news/search/top")
    public List<NewsSearchBarItemDto> getTopSearchResults(@RequestParam(name = "title") String searchTerm,
                                                          @RequestParam(name = "entriesPerPage") int entriesPerPage) {
        return newsService.getTopSearchResults(searchTerm, entriesPerPage);
    }

    @GetMapping("/api/news/search")
    public NewsSearchListDto searchNews(@RequestParam(name = "title") String searchTerm,
                                        @RequestParam(name = "pageNumber") int pageNumber,
                                        @RequestParam(name = "entriesPerPage") int entriesPerPage) {
        return newsService.searchNews(searchTerm, pageNumber, entriesPerPage);
    }

}
