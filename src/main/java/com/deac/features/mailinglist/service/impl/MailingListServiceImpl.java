package com.deac.features.mailinglist.service.impl;

import com.deac.exception.MyException;
import com.deac.features.mailinglist.dto.UnsubscribeDto;
import com.deac.features.mailinglist.persistence.entity.MailingListEntry;
import com.deac.features.mailinglist.persistence.repository.MailingListRepository;
import com.deac.features.mailinglist.service.MailingListService;
import com.deac.features.news.dto.NewsInfoDto;
import com.deac.features.news.service.NewsService;
import com.deac.mail.EmailService;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.Language;
import com.deac.user.service.UserService;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import javax.annotation.PostConstruct;
import javax.mail.MessagingException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class MailingListServiceImpl implements MailingListService {

    private final MailingListRepository mailingListRepository;

    private final EmailService emailService;

    private final UserService userService;

    private final NewsService newsService;

    private final List<String> disallowedEmails = List.of("kyokushindev@gmail.com");

    private String mailingListTemplate;

    private String mailingListArticleLineTemplate;

    private String mailingListDividerTemplate;

    @Autowired
    public MailingListServiceImpl(MailingListRepository mailingListRepository, EmailService emailService, UserService userService, NewsService newsService) {
        this.mailingListRepository = mailingListRepository;
        this.emailService = emailService;
        this.userService = userService;
        this.newsService = newsService;
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource mailingListTemplateResource = resourceLoader.getResource("classpath:templates/MailingListTemplate.html");
        Resource mailingListArticleLineResource = resourceLoader.getResource("classpath:templates/MailingListArticleLineTemplate.html");
        Resource mailingListDividerResource = resourceLoader.getResource("classpath:templates/MailingListDividerTemplate.html");
        try (
                Reader mailingListTemplateReader = new InputStreamReader(mailingListTemplateResource.getInputStream(), StandardCharsets.UTF_8);
                Reader mailingListArticleLineTemplateReader = new InputStreamReader(mailingListArticleLineResource.getInputStream(), StandardCharsets.UTF_8);
                Reader mailingListDividerTemplateReader = new InputStreamReader(mailingListDividerResource.getInputStream(), StandardCharsets.UTF_8)
        ) {
            mailingListTemplate = FileCopyUtils.copyToString(mailingListTemplateReader);
            mailingListArticleLineTemplate = FileCopyUtils.copyToString(mailingListArticleLineTemplateReader);
            mailingListDividerTemplate = FileCopyUtils.copyToString(mailingListDividerTemplateReader);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isClientSubscribed() {
        User currentUser = userService.getCurrentUser();
        return mailingListRepository.existsByEmail(currentUser.getEmail());
    }

    @Override
    public String clientSubscribeToMailingList() {
        User currentUser = userService.getCurrentUser();
        return subscribeToMailingList(currentUser.getEmail(), currentUser.getLanguage());
    }

    @Override
    public String clientUnsubscribeFromMailingList() {
        User currentUser = userService.getCurrentUser();
        MailingListEntry mailingListEntry = mailingListRepository.findByEmail(currentUser.getEmail()).orElseThrow(() -> new MyException("You are not subscribed to our mailing list", HttpStatus.BAD_REQUEST));
        mailingListRepository.delete(mailingListEntry);
        return "Successfully unsubscribed from our mailing list!";
    }

    @Override
    public String subscribeToMailingList(String email, Language language) {
        if (mailingListRepository.existsByEmail(email)) {
            throw new MyException("You are already subscribed", HttpStatus.BAD_REQUEST);
        }
        if (disallowedEmails.contains(email)) {
            throw new MyException("Admins cannot subscribe to our mailing list", HttpStatus.BAD_REQUEST);
        }
        MailingListEntry mailingListEntry = new MailingListEntry(email);
        mailingListEntry.setLanguage(language);
        String token = RandomStringUtils.randomAlphanumeric(64, 96);
        mailingListEntry.setTokenValue(token);
        mailingListRepository.save(mailingListEntry);
        return "Successfully subscribed to our mailing list!";
    }

    @Override
    public String unsubscribeFromMailingList(UnsubscribeDto unsubscribeDto) {
        MailingListEntry mailingListEntry = mailingListRepository.findByEmail(unsubscribeDto.getEmail()).orElseThrow(() -> new MyException("You are not subscribed to our mailing list", HttpStatus.BAD_REQUEST));
        if (!mailingListEntry.getTokenValue().equals(unsubscribeDto.getToken())) {
            throw new MyException("Unsubscribe failed", HttpStatus.BAD_REQUEST);
        }
        mailingListRepository.delete(mailingListEntry);
        return "Successfully unsubscribed from our mailing list!";
    }

    @Scheduled(cron = "0 0 0 * * 0")
    @PostConstruct
    public void sendOutWeeklyMails() {
        List<NewsInfoDto> latestNews = newsService.getLatestNews(5);
        StringBuilder articleLines = new StringBuilder();
        for (int i = 0; i < latestNews.size(); i++) {
            NewsInfoDto news = latestNews.get(i);
            String truncatedTitle = news.getTitle().length() >= 50 ? (news.getTitle().substring(0, 50) + "...") : news.getTitle();
            String truncatedDescription = news.getDescription().length() >= 50 ? (news.getDescription().substring(0, 50) + "...") : news.getDescription();
            String articleLine = mailingListArticleLineTemplate.replace("[NEWS_TITLE]", truncatedTitle).replace("[NEWS_DESCRIPTION]", truncatedDescription);
            articleLines.append(articleLine);
            if (i != latestNews.size() - 1) {
                articleLines.append(mailingListDividerTemplate);
            }
        }
        mailingListRepository.findAll().forEach(mailingListEntry -> {
            try {
                sendMembershipEmail(mailingListEntry, articleLines.toString());
            } catch (MessagingException ignored) {
            }
        });
    }

    private void sendMembershipEmail(MailingListEntry mailingListEntry, String articleLines) throws MessagingException {
        String subject = "";
        String line1 = "";
        String line2 = "";
        String line3 = "";
        switch (mailingListEntry.getLanguage()) {
            case HU:
                subject = "A heti hírlevele";
                line1 = "A legfrissebb hírek erről a hétről:";
                line2 = "Amennyiben le szeretne iratkozni hírlevelünkről, itt teheti meg:";
                line3 = "Leiratkozás";
                break;
            case EN:
                subject = "Your weekly newsletter";
                line1 = "The latest news this week:";
                line2 = "If you wish to unsubscribe from our newsletter, click here:";
                line3 = "Unsubscribe";
                break;
            default:
                throw new MyException("Unsupported language", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        String emailBody = mailingListTemplate
                .replace("[LINE_1]", line1)
                .replace("[ARTICLE_ROWS]", articleLines)
                .replace("[LINE_2]", line2)
                .replace("[UNSUBSCRIBE_EMAIL]", mailingListEntry.getEmail())
                .replace("[UNSUBSCRIBE_TOKEN]", mailingListEntry.getTokenValue())
                .replace("[LINE_3]", line3);
        emailService.sendMessage(mailingListEntry.getEmail(),
                subject,
                emailBody,
                List.of());
    }

}
