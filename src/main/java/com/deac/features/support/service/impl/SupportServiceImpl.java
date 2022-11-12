package com.deac.features.support.service.impl;

import com.deac.exception.MyException;
import com.deac.features.support.dto.*;
import com.deac.features.support.persistence.entity.Ticket;
import com.deac.features.support.persistence.entity.TicketComment;
import com.deac.features.support.persistence.repository.SupportRepository;
import com.deac.features.support.service.SupportService;
import com.deac.mail.Attachment;
import com.deac.mail.EmailService;
import com.deac.user.persistence.entity.Role;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.UserService;
import org.apache.commons.lang3.RandomStringUtils;
import org.hibernate.Hibernate;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.mail.MessagingException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SupportServiceImpl implements SupportService {

    private final SupportRepository supportRepository;

    private final UserService userService;

    private final EmailService emailService;

    private final String ticketAttachmentUploadBaseDirectory;

    private String supportTemplate;

    @Autowired
    public SupportServiceImpl(SupportRepository supportRepository, UserService userService, EmailService emailService, Environment environment) {
        this.supportRepository = supportRepository;
        this.userService = userService;
        this.emailService = emailService;
        ticketAttachmentUploadBaseDirectory = Objects.requireNonNull(environment.getProperty("file.tickets.rootdir", String.class));
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource emailTemplateResource = resourceLoader.getResource("classpath:templates/EmailTemplate.html");
        Resource supportTemplateResource = resourceLoader.getResource("classpath:templates/SupportTemplate.html");
        try (
                Reader emailTemplateReader = new InputStreamReader(emailTemplateResource.getInputStream(), StandardCharsets.UTF_8);
                Reader supportTemplateReader = new InputStreamReader(supportTemplateResource.getInputStream(), StandardCharsets.UTF_8)
        ) {
            String emailTemplate = FileCopyUtils.copyToString(emailTemplateReader);
            supportTemplate = emailTemplate.replace("[BODY_TEMPLATE]", FileCopyUtils.copyToString(supportTemplateReader));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    @Transactional
    public List<TicketInfoDto> listTickets(int pageNumber, int pageSize, Boolean filterTicketStatus) {
        User currentUser = userService.getCurrentUser();
        return listTicketsHelper(pageNumber, pageSize, null, filterTicketStatus, false, currentUser);
    }

    @Override
    public Long getNumberOfTickets(Boolean filterTicketStatus) {
        if (filterTicketStatus == null) {
            return supportRepository.count();
        } else {
            return supportRepository.countAllByClosed(filterTicketStatus);
        }
    }

    @Override
    @Transactional
    public String closeTicket(Integer ticketId, boolean value) {
        Ticket ticket = supportRepository.findById(ticketId).orElseThrow(() -> new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST));
        if (ticket.getIssuer() == null && ticket.isClosed() && !value) {
            throw new MyException("Anonymous tickets cannot be reopened", HttpStatus.BAD_REQUEST);
        }
        ticket.setClosed(value);
        ticket.setUpdateDate(new Date());
        supportRepository.save(ticket);
        return "Ticket status: " + (!value ? "closed" : "open") + " -> " + (value ? "closed" : "open");
    }

    @Override
    public String deleteTicket(Integer ticketId) {
        if (!supportRepository.existsById(ticketId)) {
            throw new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST);
        }
        supportRepository.deleteById(ticketId);
        return "Successfully deleted ticket";
    }

    @Override
    @Transactional
    public String deleteComment(Integer ticketId, Integer commentId) {
        Ticket ticket = supportRepository.findById(ticketId).orElseThrow(() -> new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST));
        Hibernate.initialize(ticket.getComments());
        Optional<TicketComment> optionalTicketComment = ticket.getComments().stream()
                .filter(ticketComment -> ticketComment.getId().equals(commentId))
                .findFirst();
        if (optionalTicketComment.isEmpty()) {
            throw new MyException("Comment does not exist", HttpStatus.BAD_REQUEST);
        }
        TicketComment ticketComment = optionalTicketComment.get();
        ticket.getComments().remove(ticketComment);
        supportRepository.save(ticket);
        return "Successfully deleted comment";
    }

    @Override
    @Transactional
    public List<TicketInfoDto> searchTicket(int pageNumber, int pageSize, String searchTerm) {
        User currentUser = userService.getCurrentUser();
        if ("Anonymous".equals(searchTerm)) {
            return listTicketsHelper(pageNumber, pageSize, null, null, true, currentUser);
        }
        User user = userService.getUserByUsernameOrEmail(searchTerm);
        if (user == null || user.getRoles().contains(Role.ADMIN)) {
            return List.of();
        }
        return listTicketsHelper(pageNumber, pageSize, user, null, false, currentUser);
    }

    @Override
    public Long getNumberOfSearchResults(String searchTerm) {
        if ("Anonymous".equals(searchTerm)) {
            return supportRepository.countByIssuerIsNull();
        }
        User user = userService.getUserByUsernameOrEmail(searchTerm);
        if (user == null || user.getRoles().contains(Role.ADMIN)) {
            return 0L;
        }
        return supportRepository.countByIssuer(user);
    }

    @Override
    public Long getAdminNumberOfUnopenedTickets() {
        return supportRepository.countByViewed(false);
    }

    @Override
    public String markTicketAsRead(Integer ticketId) {
        Ticket ticket = supportRepository.findById(ticketId).orElseThrow(() -> new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST));
        if (ticket.isViewed()) {
            throw new MyException("Ticket already viewed", HttpStatus.BAD_REQUEST);
        }
        ticket.setViewed(true);
        supportRepository.save(ticket);
        return "Successfully marked ticket as read";
    }

    @Override
    public Integer createTicket(String content, MultipartFile[] files) {
        User currentUser = userService.getCurrentUser();
        if (currentUser.getRoles().contains(Role.ADMIN)) {
            throw new MyException("Admins cannot create tickets", HttpStatus.BAD_REQUEST);
        }
        if (files.length > 5) {
            throw new MyException("Maximum number of files allowed is 5", HttpStatus.BAD_REQUEST);
        }
        Ticket ticket = new Ticket(
                "Ticket-" + RandomStringUtils.random(8, "0123456789abcdef"),
                content,
                currentUser,
                currentUser.getEmail()
        );
        List<Attachment> savedFiles = uploadAttachments(files, currentUser.getId(), ticket.getTitle(), null);
        ticket.setAttachmentPaths(savedFiles.stream().map(Attachment::getName).collect(Collectors.toList()));
        supportRepository.save(ticket);
        return ticket.getId();
    }

    private List<Attachment> uploadAttachments(MultipartFile[] files, Integer userId, String ticketId, String commentId) {
        try {
            String baseDir;
            if (userId != null) {
                baseDir = ticketAttachmentUploadBaseDirectory + "user_" + userId + "/" + ticketId + "/" + (commentId != null ? (commentId + "/") : "");
            } else {
                baseDir = ticketAttachmentUploadBaseDirectory + "anonymous/" + ticketId + "/" + (commentId != null ? (commentId + "/") : "");
            }
            List<Attachment> savedFiles = new ArrayList<>();
            for (MultipartFile file : files) {
                if (!Objects.requireNonNull(file.getContentType()).startsWith("image/") && !file.getContentType().startsWith("application/pdf")) {
                    throw new MyException("Unsupported file type", HttpStatus.BAD_REQUEST);
                }
                byte[] fileBytes = file.getBytes();
                Files.createDirectories(Path.of(baseDir));
                Path targetPath = Path.of(baseDir + file.getOriginalFilename());
                savedFiles.add(new Attachment(Files.write(targetPath, fileBytes).getFileName().toString(), fileBytes));
            }
            return savedFiles;
        } catch (IOException e) {
            throw new MyException("File upload failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public List<TicketInfoDto> listCurrentUserTickets(int pageNumber, int pageSize, Boolean filterTicketStatus) {
        User currentUser = userService.getCurrentUser();
        return listTicketsHelper(pageNumber, pageSize, currentUser, filterTicketStatus, false, currentUser);
    }

    private List<TicketInfoDto> listTicketsHelper(int pageNumber, int pageSize, User user, Boolean filterTicketStatus, boolean anonymous, User currentUser) {
        Pageable sortedByCreateDateDesc = PageRequest.of(pageNumber - 1, pageSize, Sort.by(Sort.Order.desc("updateDate")));
        List<Ticket> tickets;
        boolean shouldFetchIssuer = true;
        if (user == null) {
            if (filterTicketStatus == null) {
                if (!anonymous) {
                    tickets = supportRepository.findBy(sortedByCreateDateDesc);
                } else {
                    tickets = supportRepository.findByIssuerIsNull(sortedByCreateDateDesc);
                    shouldFetchIssuer = false;
                }
            } else {
                tickets = supportRepository.findByClosed(filterTicketStatus, sortedByCreateDateDesc);
            }
        } else {
            if (filterTicketStatus == null) {
                tickets = supportRepository.findByIssuer(user, sortedByCreateDateDesc);
            } else {
                tickets = supportRepository.findByIssuerAndClosed(user, filterTicketStatus, sortedByCreateDateDesc);
            }
        }
        List<Integer> ids = tickets.stream().map(Ticket::getId).collect(Collectors.toList());
        if (!ids.isEmpty()) {
            if (shouldFetchIssuer) tickets = supportRepository.fetchIssuerAndRoles(ids);
            tickets = supportRepository.fetchAttachments(ids);
            tickets = supportRepository.fetchComments(ids);
        }
        return ticketListToTicketInfoDtoList(tickets, currentUser);
    }

    private List<TicketInfoDto> ticketListToTicketInfoDtoList(List<Ticket> tickets, User user) {
        return tickets.stream()
                .sorted(Comparator.comparing(Ticket::getUpdateDate).reversed())
                .map(ticket -> {
                    User issuer = ticket.getIssuer();
                    Hibernate.initialize(ticket.getComments());
                    return new TicketInfoDto(
                            ticket.getId(),
                            ticket.getTitle(),
                            ticket.getContent(),
                            (issuer != null ? ticket.getIssuer().getUsername() : "Anonymous"),
                            ticket.getCreateDate(),
                            ticket.isClosed(),
                            ticket.isViewed(),
                            ticket.getComments().stream()
                                    .filter(ticketComment -> !ticketComment.getIssuer().getId().equals(user.getId()))
                                    .filter(ticketComment -> !ticketComment.isViewed())
                                    .count()
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    public Long getNumberOfCurrentUserTickets(Boolean filterTicketStatus) {
        User currentUser = userService.getCurrentUser();
        if (filterTicketStatus == null) {
            return supportRepository.countByIssuer(currentUser);
        } else {
            return supportRepository.countAllByIssuerAndClosed(currentUser, filterTicketStatus);
        }
    }

    @Override
    @Transactional
    public TicketDetailInfoDto getTicketDetails(Integer id) {
        User currentUser = userService.getCurrentUser();
        Ticket ticket = supportRepository.findById(id).orElseThrow(() -> new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST));
        if (currentUser.getRoles().contains(Role.CLIENT) && !ticket.getIssuer().equals(currentUser)) {
            throw new MyException("You cannot view someone else's ticket", HttpStatus.BAD_REQUEST);
        }
        ticket = supportRepository.findByIdFetchAttachments(ticket.getId());
        ticket = supportRepository.findByIdFetchComments(ticket.getId());
        User issuer = ticket.getIssuer();
        return new TicketDetailInfoDto(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getContent(),
                (issuer != null ? ticket.getIssuer().getUsername() : "Anonymous"),
                ticket.getCreateDate(),
                ticket.isClosed(),
                ticket.getAttachmentPaths(),
                ticketCommentListToTicketCommentDtoList(ticket.getComments()),
                ticket.isViewed()
        );
    }

    private List<TicketCommentDto> ticketCommentListToTicketCommentDtoList(List<TicketComment> comments) {
        return comments.stream()
                .map(ticketComment -> {
                    Hibernate.initialize(ticketComment.getAttachmentPaths());
                    User issuer = ticketComment.getIssuer();
                    return new TicketCommentDto(
                            ticketComment.getId(),
                            ticketComment.getTitle(),
                            ticketComment.getContent(),
                            (issuer != null ? ticketComment.getIssuer().getUsername() : "Anonymous"),
                            ticketComment.getCreateDate(),
                            ticketComment.getAttachmentPaths(),
                            ticketComment.isViewed()
                    );
                })
                .sorted(Comparator.comparing(TicketCommentDto::getCreateDate))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AttachmentDownloadDto downloadTicketAttachment(String ticketId, String attachmentPath) {
        try {
            User currentUser = userService.getCurrentUser();
            Ticket ticket = supportRepository.findByTitle(ticketId).orElseThrow(() -> new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST));
            if (currentUser.getRoles().contains(Role.CLIENT) && !ticket.getIssuer().equals(currentUser)) {
                throw new MyException("You cannot download someone else's attachment", HttpStatus.BAD_REQUEST);
            }
            String baseDir = ticketAttachmentUploadBaseDirectory + "user_" + ticket.getIssuer().getId() + "/" + ticketId + "/";
            String targetPath = baseDir + attachmentPath;
            Path path = Path.of(targetPath);
            return new AttachmentDownloadDto(Files.probeContentType(path), Files.readAllBytes(path));
        } catch (IOException e) {
            throw new MyException("Could not download ticket attachment", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public String createComment(Integer ticketId, String content, MultipartFile[] files) {
        User currentUser = userService.getCurrentUser();
        Ticket ticket = supportRepository.findById(ticketId).orElseThrow(() -> new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST));
        if (currentUser.getRoles().contains(Role.CLIENT) && !ticket.getIssuer().equals(currentUser)) {
            throw new MyException("You cannot comment on someone else's ticket", HttpStatus.BAD_REQUEST);
        }
        if (ticket.isClosed()) {
            throw new MyException("Ticket is closed", HttpStatus.BAD_REQUEST);
        }
        Hibernate.initialize(ticket.getComments());
        TicketComment ticketComment = new TicketComment(
                "Comment-" + RandomStringUtils.random(8, "0123456789abcdef"),
                content,
                currentUser
        );
        List<Attachment> savedFiles;
        if (ticket.getIssuer() == null) {
            savedFiles = uploadAttachments(files, null, ticket.getTitle(), ticketComment.getTitle());
            ticket.setClosed(true);
            try {
                sendSupportEmail(currentUser, ticket, ticketComment, savedFiles);
            } catch (MessagingException ignored) {
            }
        } else {
            savedFiles = uploadAttachments(files, ticket.getIssuer().getId(), ticket.getTitle(), ticketComment.getTitle());
        }
        ticketComment.setAttachmentPaths(savedFiles.stream().map(Attachment::getName).collect(Collectors.toList()));
        ticket.getComments().add(ticketComment);
        ticket.setUpdateDate(new Date());
        supportRepository.save(ticket);
        return "Successfully posted comment";
    }

    private void sendSupportEmail(User currentUser, Ticket ticket, TicketComment ticketComment, List<Attachment> attachments) throws MessagingException {
        String line1 = "";
        String line2 = "";
        String line3 = "";
        String line4 = "";
        String line5 = "";
        String line6 = "";
        String line7 = "";
        switch (ticket.getIssuerLanguage()) {
            case HU:
                line1 = "Tisztelt Hölgyem/Uram,";
                line2 = "a support csapatunk átvizsgálta és megválaszolta a problémáját.";
                line3 = "<span style=\"font-weight: bold;\">Ön</span> eredetileg írta:";
                line4 = "<span style=\"font-weight: bold;\">[SUPPORT_STAFF_NAME]</span> válaszolt:";
                line5 = "Ha bármilyen további kérdése merülne fel, válaszoljon erre az email-re.";
                line6 = "Üdvözlettel,";
                line7 = "DEAC Kyokushin Karate Support Csapat";
                break;
            case EN:
                line1 = "Dear Sir/Madam,";
                line2 = "our support has reviewed and answered your issue.";
                line3 = "<span style=\"font-weight: bold;\">You</span> originally asked:";
                line4 = "<span style=\"font-weight: bold;\">[SUPPORT_STAFF_NAME]</span> replied:";
                line5 = "If you have any more questions/problems, reply to this email.";
                line6 = "Regards,";
                line7 = "DEAC Kyokushin Karate Support Staff";
                break;
            default:
                throw new MyException("Unsupported language", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        line4 = line4.replace("[SUPPORT_STAFF_NAME]", currentUser.getUsername());
        Document.OutputSettings outputSettings = new Document.OutputSettings();
        outputSettings.prettyPrint(false);
        String emailBody = supportTemplate
                .replace("[LINE_1]", line1)
                .replace("[LINE_2]", line2)
                .replace("[LINE_3]", line3)
                .replace("[ORIGINAL_CONTENT]", Jsoup.clean(ticket.getContent(), "", Safelist.none(), outputSettings))
                .replace("[LINE_4]", line4)
                .replace("[COMMENT_CONTENT]", Jsoup.clean(ticketComment.getContent(), "", Safelist.none(), outputSettings))
                .replace("[LINE_5]", line5)
                .replace("[LINE_6]", line6)
                .replace("[LINE_7]", line7);
        emailService.sendMessage(ticket.getIssuerEmail(),
                "#" + ticket.getTitle(),
                emailBody,
                attachments);
    }

    @Override
    @Transactional
    public AttachmentDownloadDto downloadTicketCommentAttachment(String ticketId, String commentId, String attachmentPath) {
        try {
            User currentUser = userService.getCurrentUser();
            Ticket ticket = supportRepository.findByTitle(ticketId).orElseThrow(() -> new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST));
            if (currentUser.getRoles().contains(Role.CLIENT) && !ticket.getIssuer().equals(currentUser)) {
                throw new MyException("You cannot download someone else's attachment", HttpStatus.BAD_REQUEST);
            }
            String baseDir;
            if (ticket.getIssuer() != null) {
                baseDir = ticketAttachmentUploadBaseDirectory + "user_" + ticket.getIssuer().getId() + "/" + ticketId + "/" + commentId + "/";
            } else {
                baseDir = ticketAttachmentUploadBaseDirectory + "anonymous/" + ticketId + "/" + commentId + "/";
            }
            String targetPath = baseDir + attachmentPath;
            Path path = Path.of(targetPath);
            return new AttachmentDownloadDto(Files.probeContentType(path), Files.readAllBytes(path));
        } catch (IOException e) {
            throw new MyException("Could not download ticket attachment", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public Long getClientNumberOfCommentNotifications() {
        User currentUser = userService.getCurrentUser();
        List<Ticket> currentUserTickets = supportRepository.findByIssuer(currentUser);
        if (!currentUserTickets.isEmpty()) {
            currentUserTickets = supportRepository.fetchComments(currentUserTickets.stream().map(Ticket::getId).collect(Collectors.toList()));
        }
        return currentUserTickets.stream()
                .mapToLong(ticket -> ticket.getComments().stream()
                        .filter(ticketComment -> !ticketComment.getIssuer().getId().equals(currentUser.getId()))
                        .filter(ticketComment -> !ticketComment.isViewed())
                        .count())
                .sum();
    }

    @Override
    @Transactional
    public String markCommentsAsRead(Integer ticketId) {
        Ticket ticket = supportRepository.findById(ticketId).orElseThrow(() -> new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST));
        User currentUser = userService.getCurrentUser();
        ticket = supportRepository.findByIdFetchComments(ticket.getId());
        List<TicketComment> comments = ticket.getComments();
        long tmp = comments.stream()
                .filter(ticketComment -> !ticketComment.getIssuer().getId().equals(currentUser.getId()) && !ticketComment.isViewed())
                .count();
        if (tmp == 0) {
            throw new MyException("All ticket comments already viewed", HttpStatus.BAD_REQUEST);
        }
        comments.stream()
                .filter(ticketComment -> !ticketComment.getIssuer().getId().equals(currentUser.getId()))
                .forEach(ticketComment -> {
                    if (!ticketComment.isViewed()) {
                        ticketComment.setViewed(true);
                    }
                });
        ticket.setComments(comments);
        supportRepository.save(ticket);
        return "Successfully marked ticket comments as read";
    }

    @Override
    public String createAnonymousTicket(TicketCreateDto ticketCreateDto) {
        Ticket ticket = new Ticket(
                "Ticket-" + RandomStringUtils.random(8, "0123456789abcdef"),
                ticketCreateDto.getContent(),
                null,
                ticketCreateDto.getIssuerEmail()
        );
        ticket.setIssuerLanguage(ticketCreateDto.getIssuerLanguage());
        supportRepository.save(ticket);
        return "Successfully created anonymous ticket";
    }

    @Scheduled(cron = "0 0 0 1 * *")
    public void deleteOldClosedTickets() {
        long timeInMillis = LocalDateTime.from(new Date().toInstant()).minusYears(1).toEpochSecond(OffsetDateTime.now().getOffset());
        supportRepository.deleteAllByUpdateDateBeforeAndClosed(timeInMillis, true);
    }

}
