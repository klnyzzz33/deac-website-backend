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

    @GetMapping("/api/admin/support/ticket/list")
    public List<TicketInfoDto> listTickets(@RequestParam(name = "pageNumber") int pageNumber,
                                           @RequestParam(name = "entriesPerPage") int entriesPerPage) {
        return supportService.listTickets(pageNumber, entriesPerPage);
    }

    @PostMapping("/api/admin/support/ticket/close")
    public ResponseMessage closeTicket(@RequestBody Integer ticketId) {
        return new ResponseMessage(supportService.closeTicket(ticketId));
    }

    @PostMapping("/api/admin/support/ticket/delete")
    public ResponseMessage deleteTicket(@RequestBody Integer ticketId) {
        return new ResponseMessage(supportService.deleteTicket(ticketId));
    }

    @PostMapping("/api/support/ticket/create")
    public Integer createTicket(@RequestParam(name = "content") String content,
                                @RequestParam(name = "file", required = false) MultipartFile[] files) {
        return supportService.createTicket(content, files);
    }

    @PostMapping("/api/support/ticket/list")
    public List<TicketInfoDto> listUserTickets(@RequestParam(name = "pageNumber") int pageNumber,
                                               @RequestParam(name = "entriesPerPage") int entriesPerPage) {
        return supportService.listCurrentUserTickets(pageNumber, entriesPerPage);
    }

    @GetMapping("/api/support/ticket/count")
    public Long getNumberOfUserTickets() {
        return supportService.getNumberOfCurrentUserTickets();
    }

    @GetMapping("/api/support/ticket/open")
    public TicketDetailInfoDto getTicketDetails(@RequestParam(name = "id") Integer id) {
        return supportService.getTicketDetails(id);
    }

    @PostMapping("/api/support/ticket/download")
    public ResponseEntity<byte[]> createComment(@RequestParam(name = "ticketId") String ticketId,
                                                @RequestParam(name = "attachmentPath") String attachmentPath) {
        AttachmentDownloadDto fileInfo = supportService.downloadCurrentUserTicketAttachment(ticketId, attachmentPath);
        return ResponseEntity.ok()
                .contentLength(fileInfo.getData().length)
                .header("Content-Type", fileInfo.getType())
                .body(fileInfo.getData());
    }

    @PostMapping("/api/support/ticket/comment")
    public ResponseMessage createComment(@RequestBody TicketCommentCreateDto ticketCommentCreateDto) {
        return new ResponseMessage(supportService.createComment(ticketCommentCreateDto));
    }

    @PostMapping("/api/support/ticket/create_anonymous")
    public Integer createAnonymousTicket(@RequestBody TicketCreateDto ticketCreateDto) {
        return supportService.createAnonymousTicket(ticketCreateDto);
    }

}
