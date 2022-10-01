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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SupportService {

    private final SupportRepository supportRepository;

    private final UserService userService;

    @Autowired
    public SupportService(SupportRepository supportRepository, UserService userService) {
        this.supportRepository = supportRepository;
        this.userService = userService;
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

    public Integer createTicket(String content) {
        User currentUser = userService.getCurrentUser();
        if (currentUser.getRoles().contains(Role.ADMIN)) {
            throw new MyException("Admins cannot create tickets", HttpStatus.BAD_REQUEST);
        }
        Ticket ticket = new Ticket(
                "Ticket-" + RandomStringUtils.random(8, "0123456789abcdef"),
                content,
                currentUser,
                currentUser.getEmail()
        );
        supportRepository.save(ticket);
        return ticket.getId();
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
        return new TicketDetailInfoDto(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getContent(),
                ticket.getIssuer().getUsername(),
                ticket.getCreateDate(),
                ticket.isClosed(),
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
