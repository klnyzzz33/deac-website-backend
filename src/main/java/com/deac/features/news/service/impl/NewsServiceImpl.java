package com.deac.features.news.service.impl;

import com.deac.features.news.dto.*;
import com.deac.features.news.persistence.entity.ModifyEntry;
import com.deac.features.news.persistence.entity.News;
import com.deac.features.news.persistence.repository.NewsRepository;
import com.deac.features.news.service.LuceneSearchResult;
import com.deac.features.news.service.NewsService;
import com.deac.exception.MyException;
import com.deac.features.news.service.SearchResult;
import com.deac.user.service.UserService;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
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
import static com.deac.misc.StringSearchHelper.normalizeSearchTerm;

@Service
public class NewsServiceImpl implements NewsService {

    private final NewsRepository newsRepository;

    private final UserService userService;

    private final String imageUploadBaseDirectory;

    private final String imageUploadBaseUrl;

    private Directory index;

    private IndexWriter writer;

    @Autowired
    public NewsServiceImpl(NewsRepository newsRepository, UserService userService, Environment environment) {
        this.newsRepository = newsRepository;
        this.userService = userService;
        imageUploadBaseDirectory = Objects.requireNonNull(environment.getProperty("files.upload.rootdir", String.class));
        imageUploadBaseUrl = Objects.requireNonNull(environment.getProperty("files.upload.baseurl", String.class));
        String searchIndexPath = Objects.requireNonNull(environment.getProperty("search.index.rootdir", String.class));
        File baseDirectory = new File(imageUploadBaseDirectory);
        if (!baseDirectory.exists()) {
            try {
                Files.createDirectories(Paths.get(imageUploadBaseDirectory));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        setupSearchIndexing(searchIndexPath);
    }

    private void setupSearchIndexing(String searchIndexPath) {
        try {
            index = new MMapDirectory(Path.of(searchIndexPath));
            StandardAnalyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            writer = new IndexWriter(index, config);
            writer.deleteAll();
            writer.commit();
            newsRepository.findAll()
                    .forEach(news -> {
                        Document document = new Document();
                        document.add(new TextField("normalizedTitle", StringUtils.join(normalizeSearchTerm(news.getTitle()), " "), Field.Store.YES));
                        document.add(new TextField("id", news.getId().toString(), Field.Store.YES));
                        try {
                            writer.addDocument(document);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
            writer.commit();
        } catch (IOException e) {
            e.printStackTrace();
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
        updateSearchIndices(news, "create", null);
        return news.getId();
    }

    @Override
    public String deleteNews(Integer newsId) {
        if (!newsRepository.existsById(newsId)) {
            throw new MyException("News does not exist", HttpStatus.BAD_REQUEST);
        }
        newsRepository.deleteById(newsId);
        updateSearchIndices(null, "delete", List.of(newsId));
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
        updateSearchIndices(null, "delete", newsIds);
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
        updateSearchIndices(news, "update", null);
        return "Successfully updated news";
    }

    private void updateSearchIndices(News news, String operation, List<Integer> ids) {
        try {
            if ("create".equals(operation)) {
                Document document = new Document();
                document.add(new TextField("normalizedTitle", StringUtils.join(normalizeSearchTerm(news.getTitle()), " "), Field.Store.YES));
                document.add(new TextField("id", news.getId().toString(), Field.Store.YES));
                writer.addDocument(document);
                writer.commit();
            } else if ("update".equals(operation)) {
                Document document = new Document();
                document.add(new TextField("normalizedTitle", StringUtils.join(normalizeSearchTerm(news.getTitle()), " "), Field.Store.YES));
                document.add(new TextField("id", news.getId().toString(), Field.Store.YES));
                writer.updateDocument(new Term("id", news.getId().toString()), document);
                writer.commit();
            } else if ("delete".equals(operation)) {
                Term[] terms = ids.stream()
                        .map(id -> new Term("id", id.toString()))
                        .toArray(Term[]::new);
                writer.deleteDocuments(terms);
                writer.commit();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    @Override
    public List<NewsSearchBarItemDto> getTopSearchResults(String searchTerm, int pageSize) {
        SearchResult searchResult = doSearch(searchTerm, 0, pageSize);
        List<Integer> searchIds = searchResult.getResults();
        return newsRepository.findAllById(searchIds).stream()
                .map(news -> new NewsSearchBarItemDto(news.getId(),
                        news.getTitle(),
                        news.getIndexImageUrl()))
                .sorted(Comparator.comparing(item -> searchIds.indexOf(item.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public NewsSearchListDto searchNews(String searchTerm, int pageNumber, int pageSize) {
        SearchResult searchResult = doSearch(searchTerm, pageNumber - 1, pageSize);
        List<Integer> searchIds = searchResult.getResults();
        List<News> results = newsRepository.findAllById(searchIds);
        results.sort(Comparator.comparing(item -> searchIds.indexOf(item.getId())));
        return new NewsSearchListDto(newsListToNewsInfoDtoList(results), searchResult.getNumberOfResults());
    }

    private SearchResult doSearch(String searchTerm, int pageNumber, int pageSize) {
        if (searchTerm == null) {
            return new SearchResult(List.of(), 0);
        }
        if (searchTerm.length() < 3) {
            return new SearchResult(List.of(), 0);
        }
        String[] searchKeywords = normalizeSearchTerm(searchTerm);
        if (searchKeywords.length == 0) {
            return new SearchResult(List.of(), 0);
        }
        LuceneSearchResult searchResult = searchIndex(searchKeywords, pageNumber, pageSize);
        return new SearchResult(
                searchResult.getResults().stream()
                        .map(indexableFields -> Integer.valueOf(indexableFields.get("id")))
                        .collect(Collectors.toList()),
                searchResult.getNumberOfResults()
        );
    }

    private LuceneSearchResult searchIndex(String[] keywords, int pageNumber, int pageSize) {
        try {
            BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
            for (String keyword : keywords) {
                Term term = new Term("normalizedTitle", keyword);
                Query query;
                if (!StringUtils.isNumeric(keyword)) {
                    query = new FuzzyQuery(term, 2, 1);
                } else {
                    query = new TermQuery(term);
                }
                queryBuilder.add(query, BooleanClause.Occur.MUST);
            }
            Query query = queryBuilder.build();
            IndexReader indexReader = DirectoryReader.open(index);
            IndexSearcher searcher = new IndexSearcher(indexReader);
            TopScoreDocCollector collector = TopScoreDocCollector.create(Math.max(1, searcher.count(query)));
            searcher.search(query, collector);
            List<Document> documents = new ArrayList<>();
            TopDocs topDocs = collector.topDocs(pageNumber * pageSize, pageSize);
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                documents.add(searcher.doc(scoreDoc.doc));
            }
            return new LuceneSearchResult(documents, topDocs.totalHits);
        } catch (IOException e) {
            throw new MyException("Unknown error occurred while searching", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
