package com.deac.features.support.controller;

import com.deac.features.support.dto.*;
import com.deac.features.support.service.SupportService;
import com.deac.response.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class SupportController {

    private final SupportService supportService;

    @Autowired
    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @PostMapping("/api/admin/support/ticket/list")
    public List<TicketInfoDto> listTickets(@RequestParam(name = "pageNumber") int pageNumber,
                                           @RequestParam(name = "entriesPerPage") int entriesPerPage,
                                           @RequestParam(name = "filterTicketStatus", required = false) Boolean filterTicketStatus) {
        return supportService.listTickets(pageNumber, entriesPerPage, filterTicketStatus);
    }

    @GetMapping("/api/admin/support/ticket/count")
    public Long getNumberOfTickets(@RequestParam(name = "filterTicketStatus", required = false) Boolean filterTicketStatus) {
        return supportService.getNumberOfTickets(filterTicketStatus);
    }

    @PostMapping("/api/admin/support/ticket/close")
    public ResponseMessage closeTicket(@RequestBody Integer ticketId, @RequestParam(name = "value") boolean value) {
        return new ResponseMessage(supportService.closeTicket(ticketId, value));
    }

    @PostMapping("/api/admin/support/ticket/delete")
    public ResponseMessage deleteTicket(@RequestBody Integer ticketId) {
        return new ResponseMessage(supportService.deleteTicket(ticketId));
    }

    @PostMapping("/api/admin/support/ticket/comment/delete")
    public ResponseMessage deleteComment(@RequestParam(name = "ticketId") Integer ticketId, @RequestBody Integer commentId) {
        return new ResponseMessage(supportService.deleteComment(ticketId, commentId));
    }

    @GetMapping("/api/admin/support/ticket/search")
    public List<TicketInfoDto> searchTicket(@RequestParam(name = "pageNumber") int pageNumber,
                                            @RequestParam(name = "entriesPerPage") int entriesPerPage,
                                            @RequestParam(name = "searchTerm") String searchTerm) {
        return supportService.searchTicket(pageNumber, entriesPerPage, searchTerm);
    }

    @GetMapping("/api/admin/support/ticket/search/count")
    public Long getNumberOfSearchResults(@RequestParam(name = "searchTerm") String searchTerm) {
        return supportService.getNumberOfSearchResults(searchTerm);
    }

    @GetMapping("/api/admin/support/ticket/notifications")
    public Long getAdminNumberOfUnopenedTickets() {
        return supportService.getAdminNumberOfUnopenedTickets();
    }

    @PostMapping("/api/admin/support/ticket/read")
    public ResponseMessage markTicketAsRead(@RequestBody Integer ticketId) {
        return new ResponseMessage(supportService.markTicketAsRead(ticketId));
    }

    @PostMapping("/api/support/ticket/create")
    public Integer createTicket(@RequestParam(name = "content") String content,
                                @RequestParam(name = "file", required = false) MultipartFile[] files) {
        return supportService.createTicket(content, files);
    }

    @PostMapping("/api/support/ticket/list")
    public List<TicketInfoDto> listCurrentUserTickets(@RequestParam(name = "pageNumber") int pageNumber,
                                                      @RequestParam(name = "entriesPerPage") int entriesPerPage,
                                                      @RequestParam(name = "filterTicketStatus", required = false) Boolean filterTicketStatus) {
        return supportService.listCurrentUserTickets(pageNumber, entriesPerPage, filterTicketStatus);
    }

    @GetMapping("/api/support/ticket/count")
    public Long getNumberOfCurrentUserTickets(@RequestParam(name = "filterTicketStatus", required = false) Boolean filterTicketStatus) {
        return supportService.getNumberOfCurrentUserTickets(filterTicketStatus);
    }

    @GetMapping("/api/support/ticket/open")
    public TicketDetailInfoDto getTicketDetails(@RequestParam(name = "id") Integer id) {
        return supportService.getTicketDetails(id);
    }

    @PostMapping("/api/support/ticket/download")
    public ResponseEntity<byte[]> downloadTicketAttachment(@RequestParam(name = "ticketId") String ticketId,
                                                           @RequestParam(name = "attachmentPath") String attachmentPath) {
        AttachmentDownloadDto fileInfo = supportService.downloadTicketAttachment(ticketId, attachmentPath);
        return ResponseEntity.ok()
                .contentLength(fileInfo.getData().length)
                .header("Content-Type", fileInfo.getType())
                .body(fileInfo.getData());
    }

    @PostMapping("/api/support/ticket/comment")
    public ResponseMessage createComment(@RequestParam(name = "ticketId") Integer ticketId,
                                         @RequestParam(name = "content") String content,
                                         @RequestParam(name = "file", required = false) MultipartFile[] files) {
        return new ResponseMessage(supportService.createComment(ticketId, content, files));
    }

    @PostMapping("/api/support/ticket/comment/download")
    public ResponseEntity<byte[]> downloadTicketCommentAttachment(@RequestParam(name = "ticketId") String ticketId,
                                                                  @RequestParam(name = "commentId") String commentId,
                                                                  @RequestParam(name = "attachmentPath") String attachmentPath) {
        AttachmentDownloadDto fileInfo = supportService.downloadTicketCommentAttachment(ticketId, commentId, attachmentPath);
        return ResponseEntity.ok()
                .contentLength(fileInfo.getData().length)
                .header("Content-Type", fileInfo.getType())
                .body(fileInfo.getData());
    }

    @GetMapping("/api/support/ticket/notifications")
    public Long getClientNumberOfCommentNotifications() {
        return supportService.getClientNumberOfCommentNotifications();
    }

    @PostMapping("/api/support/ticket/comment/read")
    public ResponseMessage markCommentsAsRead(@RequestBody Integer ticketId) {
        return new ResponseMessage(supportService.markCommentsAsRead(ticketId));
    }

    @PostMapping("/api/support/ticket/create_anonymous")
    public ResponseMessage createAnonymousTicket(@RequestBody TicketCreateDto ticketCreateDto) {
        return new ResponseMessage(supportService.createAnonymousTicket(ticketCreateDto));
    }

}
