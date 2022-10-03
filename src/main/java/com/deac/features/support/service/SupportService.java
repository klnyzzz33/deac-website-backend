package com.deac.features.support.service;

import com.deac.features.support.dto.AttachmentDownloadDto;
import com.deac.features.support.dto.TicketCreateDto;
import com.deac.features.support.dto.TicketDetailInfoDto;
import com.deac.features.support.dto.TicketInfoDto;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SupportService {

    List<TicketInfoDto> listTickets(int pageNumber, int pageSize, Boolean filterTicketStatus);

    Long getNumberOfTickets(Boolean filterTicketStatus);

    String closeTicket(Integer ticketId, boolean value);

    String deleteTicket(Integer ticketId);

    @Transactional
    String deleteComment(Integer ticketId, Integer commentId);

    List<TicketInfoDto> searchTicket(int pageNumber, int pageSize, String searchTerm);

    Long getNumberOfSearchResults(String searchTerm);

    Integer createTicket(String content, MultipartFile[] files);

    List<TicketInfoDto> listCurrentUserTickets(int pageNumber, int pageSize, Boolean filterTicketStatus);

    Long getNumberOfCurrentUserTickets(Boolean filterTicketStatus);

    @Transactional
    TicketDetailInfoDto getTicketDetails(Integer id);

    AttachmentDownloadDto downloadTicketAttachment(String ticketId, String attachmentPath);

    @Transactional
    String createComment(Integer ticketId, String content, MultipartFile[] files);

    AttachmentDownloadDto downloadTicketCommentAttachment(String ticketId, String commentId, String attachmentPath);

    String createAnonymousTicket(TicketCreateDto ticketCreateDto);

}
