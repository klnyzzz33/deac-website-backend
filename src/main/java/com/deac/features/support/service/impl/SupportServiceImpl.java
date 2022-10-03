package com.deac.features.support.service.impl;

import com.deac.exception.MyException;
import com.deac.features.support.dto.*;
import com.deac.features.support.persistence.entity.Ticket;
import com.deac.features.support.persistence.entity.TicketComment;
import com.deac.features.support.persistence.repository.SupportRepository;
import com.deac.features.support.service.SupportService;
import com.deac.mail.EmailService;
import com.deac.user.persistence.entity.Role;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.UserService;
import org.apache.commons.lang3.RandomStringUtils;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.mail.MessagingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SupportServiceImpl implements SupportService {

    private final SupportRepository supportRepository;

    private final UserService userService;

    private final EmailService emailService;

    private final String ticketAttachmentUploadBaseDirectory;

    @Autowired
    public SupportServiceImpl(SupportRepository supportRepository, UserService userService, EmailService emailService, Environment environment) {
        this.supportRepository = supportRepository;
        this.userService = userService;
        this.emailService = emailService;
        ticketAttachmentUploadBaseDirectory = Objects.requireNonNull(environment.getProperty("file.tickets.rootdir", String.class));
    }

    @Override
    public List<TicketInfoDto> listTickets(int pageNumber, int pageSize, Boolean filterTicketStatus) {
        return listTicketsHelper(pageNumber, pageSize, null, filterTicketStatus, false);
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
    public String closeTicket(Integer ticketId, boolean value) {
        Ticket ticket = supportRepository.findById(ticketId).orElseThrow(() -> new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST));
        if (ticket.getIssuer() == null && ticket.isClosed() && !value) {
            throw new MyException("Anonymous tickets cannot be reopened", HttpStatus.BAD_REQUEST);
        }
        ticket.setClosed(value);
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
    public List<TicketInfoDto> searchTicket(int pageNumber, int pageSize, String searchTerm) {
        if ("Anonymous".equals(searchTerm)) {
            return listTicketsHelper(pageNumber, pageSize, null, null, true);
        }
        User user = userService.getUserByUsernameOrEmail(searchTerm);
        if (user == null || user.getRoles().contains(Role.ADMIN)) {
            return List.of();
        }
        return listTicketsHelper(pageNumber, pageSize, user, null, false);
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
        List<String> savedFileNames = uploadAttachments(files, currentUser.getId(), ticket.getTitle(), null);
        ticket.setAttachmentPaths(savedFileNames);
        supportRepository.save(ticket);
        return ticket.getId();
    }

    private List<String> uploadAttachments(MultipartFile[] files, Integer userId, String ticketId, String commentId) {
        try {
            String baseDir;
            if (userId != null) {
                baseDir = ticketAttachmentUploadBaseDirectory + "user_" + userId + "/" + ticketId + "/" + (commentId != null ? (commentId + "/") : "");
            } else {
                baseDir = ticketAttachmentUploadBaseDirectory + "anonymous/" + ticketId + "/" + (commentId != null ? (commentId + "/") : "");
            }
            List<String> savedFileNames = new ArrayList<>();
            for (MultipartFile file : files) {
                if (!Objects.requireNonNull(file.getContentType()).startsWith("image/") && !file.getContentType().startsWith("application/pdf")) {
                    throw new MyException("Unsupported file type", HttpStatus.BAD_REQUEST);
                }
                byte[] fileBytes = file.getBytes();
                Files.createDirectories(Path.of(baseDir));
                Path targetPath = Path.of(baseDir + file.getOriginalFilename());
                savedFileNames.add(Files.write(targetPath, fileBytes).getFileName().toString());
            }
            return savedFileNames;
        } catch (IOException e) {
            throw new MyException("File upload failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public List<TicketInfoDto> listCurrentUserTickets(int pageNumber, int pageSize, Boolean filterTicketStatus) {
        User currentUser = userService.getCurrentUser();
        return listTicketsHelper(pageNumber, pageSize, currentUser, filterTicketStatus, false);
    }

    private List<TicketInfoDto> listTicketsHelper(int pageNumber, int pageSize, User user, Boolean filterTicketStatus, boolean anonymous) {
        Pageable sortedByCreateDateDesc = PageRequest.of(pageNumber - 1, pageSize, Sort.by("createDate").descending());
        List<Ticket> tickets;
        if (user == null) {
            if (filterTicketStatus == null) {
                if (!anonymous) {
                    tickets = supportRepository.findBy(sortedByCreateDateDesc);
                } else {
                    tickets = supportRepository.findByIssuerIsNull(sortedByCreateDateDesc);
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
        return ticketListToTicketInfoDtoList(tickets);
    }

    private List<TicketInfoDto> ticketListToTicketInfoDtoList(List<Ticket> tickets) {
        return tickets.stream()
                .map(ticket -> {
                    User issuer = ticket.getIssuer();
                    return new TicketInfoDto(
                            ticket.getId(),
                            ticket.getTitle(),
                            ticket.getContent(),
                            (issuer != null ? ticket.getIssuer().getUsername() : "Anonymous"),
                            ticket.getCreateDate(),
                            ticket.isClosed()
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
        Hibernate.initialize(ticket.getAttachmentPaths());
        Hibernate.initialize(ticket.getComments());
        User issuer = ticket.getIssuer();
        return new TicketDetailInfoDto(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getContent(),
                (issuer != null ? ticket.getIssuer().getUsername() : "Anonymous"),
                ticket.getCreateDate(),
                ticket.isClosed(),
                ticket.getAttachmentPaths(),
                ticketCommentListToTicketCommentDtoList(ticket.getComments())
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
                            ticketComment.getAttachmentPaths()
                    );
                })
                .sorted(Comparator.comparing(TicketCommentDto::getCreateDate))
                .collect(Collectors.toList());
    }

    @Override
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
        List<String> savedFileNames;
        if (ticket.getIssuer() == null) {
            savedFileNames = uploadAttachments(files, null, ticket.getTitle(), ticketComment.getTitle());
            ticket.setClosed(true);
            try {
                emailService.sendMessage(ticket.getIssuerEmail(),
                        "#" + ticket.getTitle(),
                        "<h3>Dear Sir/Madam,<br>our support has reviewed and answered your issue:</h3><hr><b>You</b> originally asked:<br>``` " + ticket.getContent() + " ```<br><br><b>" + currentUser.getUsername() + "</b> said:<br>``` " + ticketComment.getContent() + " ```<br>If you have any more questions/problems, reply to this email.<br>Regards,<br><b>DEAC Kyokushin Karate Support Staff</b>",
                        List.of());
            } catch (MessagingException ignored) {
            }
        } else {
            savedFileNames = uploadAttachments(files, ticket.getIssuer().getId(), ticket.getTitle(), ticketComment.getTitle());
        }
        ticketComment.setAttachmentPaths(savedFileNames);
        ticket.getComments().add(ticketComment);
        supportRepository.save(ticket);
        return "Successfully posted comment";
    }

    @Override
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
    public String createAnonymousTicket(TicketCreateDto ticketCreateDto) {
        Ticket ticket = new Ticket(
                "Ticket-" + RandomStringUtils.random(8, "0123456789abcdef"),
                ticketCreateDto.getContent(),
                null,
                ticketCreateDto.getIssuerEmail()
        );
        supportRepository.save(ticket);
        return "Successfully created anonymous ticket";
    }

}
