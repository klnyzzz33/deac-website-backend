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
                                           @RequestParam(name = "entriesPerPage") int entriesPerPage) {
        return supportService.listTickets(pageNumber, entriesPerPage);
    }

    @GetMapping("/api/admin/support/ticket/count")
    public Long getNumberOfTickets() {
        return supportService.getNumberOfTickets();
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

    @PostMapping("/api/support/ticket/create")
    public Integer createTicket(@RequestParam(name = "content") String content,
                                @RequestParam(name = "file", required = false) MultipartFile[] files) {
        return supportService.createTicket(content, files);
    }

    @PostMapping("/api/support/ticket/list")
    public List<TicketInfoDto> listCurrentUserTickets(@RequestParam(name = "pageNumber") int pageNumber,
                                                      @RequestParam(name = "entriesPerPage") int entriesPerPage) {
        return supportService.listCurrentUserTickets(pageNumber, entriesPerPage);
    }

    @GetMapping("/api/support/ticket/count")
    public Long getNumberOfCurrentUserTickets() {
        return supportService.getNumberOfCurrentUserTickets();
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

    @PostMapping("/api/support/ticket/create_anonymous")
    public Integer createAnonymousTicket(@RequestBody TicketCreateDto ticketCreateDto) {
        return supportService.createAnonymousTicket(ticketCreateDto);
    }

}