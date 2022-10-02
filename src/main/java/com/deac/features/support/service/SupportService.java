package com.deac.features.support.service;

import com.deac.exception.MyException;
import com.deac.features.support.dto.*;
import com.deac.features.support.persistence.entity.Ticket;
import com.deac.features.support.persistence.entity.TicketComment;
import com.deac.features.support.persistence.repository.SupportRepository;
import com.deac.user.persistence.entity.Role;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.UserService;
import org.apache.commons.io.FilenameUtils;
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

    public List<TicketInfoDto> listTickets(int pageNumber, int pageSize) {
        return listTicketsHelper(pageNumber, pageSize, null);
    }

    public String closeTicket(Integer ticketId) {
        Ticket ticket = supportRepository.findById(ticketId).orElseThrow(() -> new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST));
        ticket.setClosed(true);
        supportRepository.save(ticket);
        return "Successfully deleted ticket";
    }

    public String deleteTicket(Integer ticketId) {
        if (!supportRepository.existsById(ticketId)) {
            throw new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST);
        }
        supportRepository.deleteById(ticketId);
        return "Successfully deleted ticket";
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
        List<String> savedFileNames = uploadAttachments(files, ticket.getTitle(), currentUser);
        ticket.setAttachmentPaths(savedFileNames);
        supportRepository.save(ticket);
        return ticket.getId();
    }

    private List<String> uploadAttachments(MultipartFile[] files, String ticketId, User currentUser) {
        try {
            String baseDir = ticketAttachmentUploadBaseDirectory + "user_" + currentUser.getId() + "/" + ticketId + "/";
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

    public List<TicketInfoDto> listCurrentUserTickets(int pageNumber, int pageSize) {
        User currentUser = userService.getCurrentUser();
        return listTicketsHelper(pageNumber, pageSize, currentUser);
    }

    private List<TicketInfoDto> listTicketsHelper(int pageNumber, int pageSize, User user) {
        Pageable sortedByCreateDateDesc = PageRequest.of(pageNumber - 1, pageSize, Sort.by("createDate").descending());
        List<Ticket> tickets;
        if (user == null) {
            tickets = supportRepository.findBy(sortedByCreateDateDesc);
        } else {
            tickets = supportRepository.findByIssuer(user, sortedByCreateDateDesc);
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

    public Long getNumberOfCurrentUserTickets() {
        User currentUser = userService.getCurrentUser();
        return supportRepository.countByIssuer(currentUser);
    }

    @Transactional
    public TicketDetailInfoDto getTicketDetails(Integer id) {
        Ticket ticket = supportRepository.findById(id).orElseThrow(() -> new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST));
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
                    User issuer = ticketComment.getIssuer();
                    return new TicketCommentDto(
                            ticketComment.getContent(),
                            ticketComment.getIssuer().getUsername(),
                            issuer.getRoles(),
                            ticketComment.getCreateDate()
                    );
                })
                .sorted(Comparator.comparing(TicketCommentDto::getCreateDate))
                .collect(Collectors.toList());
    }

    public AttachmentDownloadDto downloadCurrentUserTicketAttachment(String ticketId, String attachmentPath) {
        try {
            User currentUser = userService.getCurrentUser();
            String baseDir = ticketAttachmentUploadBaseDirectory + "user_" + currentUser.getId() + "/" + ticketId + "/";
            String targetPath = baseDir + attachmentPath;
            Path path = Path.of(targetPath);
            return new AttachmentDownloadDto(Files.probeContentType(path), Files.readAllBytes(path));
        } catch (IOException e) {
            throw new MyException("Could not download ticket attachment", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public String createComment(TicketCommentCreateDto ticketCommentCreateDto) {
        Ticket ticket = supportRepository.findById(ticketCommentCreateDto.getTicketId()).orElseThrow(() -> new MyException("Ticket does not exist", HttpStatus.BAD_REQUEST));
        TicketComment ticketComment = new TicketComment(
                ticketCommentCreateDto.getContent(),
                userService.getCurrentUser()
        );
        ticket.getComments().add(ticketComment);
        supportRepository.save(ticket);
        return "Successfully posted comment";
    }

}
