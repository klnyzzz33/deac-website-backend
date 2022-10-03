package com.deac.features.support.service;

import com.deac.exception.MyException;
import com.deac.features.support.dto.*;
import com.deac.features.support.persistence.entity.Ticket;
import com.deac.features.support.persistence.entity.TicketComment;
import com.deac.features.support.persistence.repository.SupportRepository;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SupportService {

    private final SupportRepository supportRepository;

    private final UserService userService;

    private final String ticketAttachmentUploadBaseDirectory;

    @Autowired
    public SupportService(SupportRepository supportRepository, UserService userService, Environment environment) {
        this.supportRepository = supportRepository;
        this.userService = userService;
        ticketAttachmentUploadBaseDirectory = Objects.requireNonNull(environment.getProperty("file.tickets.rootdir", String.class));
    }

    public List<TicketInfoDto> listTickets(int pageNumber, int pageSize, Boolean filterTicketStatus) {
        return listTicketsHelper(pageNumber, pageSize, null, filterTicketStatus);
    }

    public Long getNumberOfTickets(Boolean filterTicketStatus) {
        if (filterTicketStatus == null) {
            return supportRepository.count();
        } else {
            return supportRepository.countAllByClosed(filterTicketStatus);
        }
    }

    public String closeTicket(Integer ticketId, boolean value) {
        Ticket ticket = supportRepository.findById(ticketId).orElseThrow(() -> new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST));
        ticket.setClosed(value);
        supportRepository.save(ticket);
        return "Ticket status: " + (!value ? "closed" : "open") + " -> " + (value ? "closed" : "open");
    }

    public String deleteTicket(Integer ticketId) {
        if (!supportRepository.existsById(ticketId)) {
            throw new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST);
        }
        supportRepository.deleteById(ticketId);
        return "Successfully deleted ticket";
    }

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

    public List<TicketInfoDto> searchTicket(int pageNumber, int pageSize, String searchTerm) {
        User user = userService.getUserByUsernameOrEmail(searchTerm);
        if (user == null || user.getRoles().contains(Role.ADMIN)) {
            return List.of();
        }
        return listTicketsHelper(pageNumber, pageSize, user, null);
    }

    public Long getNumberOfSearchResults(String searchTerm) {
        User user = userService.getUserByUsernameOrEmail(searchTerm);
        if (user == null || user.getRoles().contains(Role.ADMIN)) {
            return 0L;
        }
        return supportRepository.countByIssuer(user);
    }

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
            String baseDir = ticketAttachmentUploadBaseDirectory + "user_" + userId + "/" + ticketId + "/" + (commentId != null ? (commentId + "/") : "");
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

    public List<TicketInfoDto> listCurrentUserTickets(int pageNumber, int pageSize, Boolean filterTicketStatus) {
        User currentUser = userService.getCurrentUser();
        return listTicketsHelper(pageNumber, pageSize, currentUser, filterTicketStatus);
    }

    private List<TicketInfoDto> listTicketsHelper(int pageNumber, int pageSize, User user, Boolean filterTicketStatus) {
        Pageable sortedByCreateDateDesc = PageRequest.of(pageNumber - 1, pageSize, Sort.by("createDate").descending());
        List<Ticket> tickets;
        if (user == null) {
            if (filterTicketStatus == null) {
                tickets = supportRepository.findBy(sortedByCreateDateDesc);
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
                .map(ticket -> new TicketInfoDto(
                        ticket.getId(),
                        ticket.getTitle(),
                        ticket.getContent(),
                        ticket.getIssuer().getUsername(),
                        ticket.getCreateDate(),
                        ticket.isClosed()
                ))
                .collect(Collectors.toList());
    }

    public Long getNumberOfCurrentUserTickets(Boolean filterTicketStatus) {
        User currentUser = userService.getCurrentUser();
        if (filterTicketStatus == null) {
            return supportRepository.countByIssuer(currentUser);
        } else {
            return supportRepository.countAllByIssuerAndClosed(currentUser, filterTicketStatus);
        }
    }

    @Transactional
    public TicketDetailInfoDto getTicketDetails(Integer id) {
        User currentUser = userService.getCurrentUser();
        Ticket ticket = supportRepository.findById(id).orElseThrow(() -> new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST));
        if (currentUser.getRoles().contains(Role.CLIENT) && !ticket.getIssuer().equals(currentUser)) {
            throw new MyException("You cannot view someone else's ticket", HttpStatus.BAD_REQUEST);
        }
        Hibernate.initialize(ticket.getAttachmentPaths());
        Hibernate.initialize(ticket.getComments());
        return new TicketDetailInfoDto(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getContent(),
                ticket.getIssuer().getUsername(),
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
                    return new TicketCommentDto(
                            ticketComment.getId(),
                            ticketComment.getTitle(),
                            ticketComment.getContent(),
                            ticketComment.getIssuer().getUsername(),
                            ticketComment.getCreateDate(),
                            ticketComment.getAttachmentPaths()
                    );
                })
                .sorted(Comparator.comparing(TicketCommentDto::getCreateDate))
                .collect(Collectors.toList());
    }

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
        List<String> savedFileNames = uploadAttachments(files, ticket.getIssuer().getId(), ticket.getTitle(), ticketComment.getTitle());
        ticketComment.setAttachmentPaths(savedFileNames);
        ticket.getComments().add(ticketComment);
        supportRepository.save(ticket);
        return "Successfully posted comment";
    }

    public AttachmentDownloadDto downloadTicketCommentAttachment(String ticketId, String commentId, String attachmentPath) {
        try {
            User currentUser = userService.getCurrentUser();
            Ticket ticket = supportRepository.findByTitle(ticketId).orElseThrow(() -> new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST));
            if (currentUser.getRoles().contains(Role.CLIENT) && !ticket.getIssuer().equals(currentUser)) {
                throw new MyException("You cannot download someone else's attachment", HttpStatus.BAD_REQUEST);
            }
            String baseDir = ticketAttachmentUploadBaseDirectory + "user_" + ticket.getIssuer().getId() + "/" + ticketId + "/" + commentId + "/";
            String targetPath = baseDir + attachmentPath;
            Path path = Path.of(targetPath);
            return new AttachmentDownloadDto(Files.probeContentType(path), Files.readAllBytes(path));
        } catch (IOException e) {
            throw new MyException("Could not download ticket attachment", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Integer createAnonymousTicket(TicketCreateDto ticketCreateDto) {
        Ticket ticket = new Ticket(
                "Ticket-" + RandomStringUtils.random(8, "0123456789abcdef"),
                ticketCreateDto.getContent(),
                null,
                ticketCreateDto.getIssuerEmail()
        );
        supportRepository.save(ticket);
        return ticket.getId();
    }

}
