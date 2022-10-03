package com.deac.features.news.service.impl;

import com.deac.features.news.dto.ModifyDto;
import com.deac.features.news.dto.ModifyInfoDto;
import com.deac.features.news.dto.NewsInfoDto;
import com.deac.features.news.persistence.entity.ModifyEntry;
import com.deac.features.news.persistence.entity.News;
import com.deac.features.news.persistence.repository.NewsRepository;
import com.deac.features.news.service.NewsService;
import com.deac.exception.MyException;
import com.deac.user.service.UserService;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static com.deac.misc.RandomStringHelper.generateRandomString;

@Service
public class NewsServiceImpl implements NewsService {

    private final NewsRepository newsRepository;

    private final UserService userService;

    private final String imageUploadBaseDirectory;

    private final String imageUploadBaseUrl;

    @Autowired
    public NewsServiceImpl(NewsRepository newsRepository, UserService userService, Environment environment) {
        this.newsRepository = newsRepository;
        this.userService = userService;
        imageUploadBaseDirectory = Objects.requireNonNull(environment.getProperty("files.upload.rootdir", String.class));
        imageUploadBaseUrl = Objects.requireNonNull(environment.getProperty("files.upload.baseurl", String.class));
        File baseDirectory = new File(imageUploadBaseDirectory);
        if (!baseDirectory.exists()) {
            try {
                Files.createDirectories(Paths.get(imageUploadBaseDirectory));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public String uploadImage(MultipartFile file) {
        try {
            if (!Objects.requireNonNull(file.getContentType()).startsWith("image/")) {
                throw new MyException("Not an image file", HttpStatus.BAD_REQUEST);
            }
            byte[] fileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();
            String randomizedFilename = FilenameUtils.getBaseName(originalFilename)
                    + "-"
                    + generateRandomString(10)
                    + "."
                    + FilenameUtils.getExtension(originalFilename);
            Path targetPath = Paths.get(imageUploadBaseDirectory + randomizedFilename);
            return Files.write(targetPath, fileBytes).getFileName().toString();
        } catch (IOException e) {
            throw new MyException("Image upload failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Integer createNews(String title, String description, String content, String indexImageUrl) {
        if (indexImageUrl == null || indexImageUrl.isEmpty()) {
            indexImageUrl = imageUploadBaseUrl + "news-icon.png";
        } else {
            indexImageUrl = imageUploadBaseUrl + indexImageUrl;
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
        if (modifyDto.isIndexImageModified()) {
            String indexImageUrl = modifyDto.getIndexImageUrl();
            if (indexImageUrl == null || indexImageUrl.isEmpty()) {
                indexImageUrl = imageUploadBaseUrl + "news-icon.png";
            } else {
                indexImageUrl = imageUploadBaseUrl + indexImageUrl;
            }
            news.setIndexImageUrl(indexImageUrl);
        }
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

    @Override
    public List<NewsInfoDto> listNewsByAuthor(String author, int pageNumber, int pageSize) {
        Pageable sortedByCreateDateDesc = PageRequest.of(pageNumber - 1, pageSize, Sort.by("createDate").descending());
        Integer userId = userService.getUserByUsername(author).getId();
        List<News> newsList = newsRepository.findByAuthorId(userId, sortedByCreateDateDesc);
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
    public long getNumberOfNewsByAuthor(String author) {
        Integer userId = userService.getUserByUsername(author).getId();
        return newsRepository.countByAuthorId(userId);
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
