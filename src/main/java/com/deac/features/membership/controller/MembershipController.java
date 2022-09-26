package com.deac.features.membership.controller;

import com.deac.features.membership.dto.*;
import com.deac.features.membership.service.MembershipService;
import com.deac.response.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
public class MembershipController {

    private final MembershipService membershipService;

    @Autowired
    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @PostMapping("/api/admin/memberships/enable")
    public ResponseMessage toggleUserEnabled(@Valid @RequestBody MembershipDto membershipDto) {
        return new ResponseMessage(membershipService.toggleUserEnabled(membershipDto.getUsername(), membershipDto.isModifiedBoolean()));
    }

    @PostMapping("/api/admin/memberships/approve")
    public ResponseMessage toggleApproved(@Valid @RequestBody MembershipDto membershipDto) {
        return new ResponseMessage(membershipService.toggleApproved(membershipDto.getUsername(), membershipDto.isModifiedBoolean()));
    }

    @GetMapping("/api/admin/memberships/list")
    public List<MembershipEntryInfoDto> listMembershipEntries(@RequestParam(name = "pageNumber") int pageNumber,
                                                              @RequestParam(name = "entriesPerPage") int entriesPerPage) {
        return membershipService.listMembershipEntries(pageNumber, entriesPerPage);
    }

    @GetMapping("/api/admin/memberships/count")
    public Long getNumberOfMembershipEntries() {
        return membershipService.getNumberOfMemberships();
    }

    @PostMapping("/api/admin/memberships/profile")
    public UserProfileDto getUserProfileData(@RequestBody String username) {
        return membershipService.getUserProfileData(username);
    }

    @PostMapping("/api/admin/memberships/profile/transactions/list")
    public List<MonthlyTransactionDto> listUserTransactions(@RequestBody String username) {
        return membershipService.listUserTransactions(username);
    }

    @PostMapping("/api/admin/memberships/profile/transactions/download")
    public ResponseEntity<byte[]> downloadUserReceipt(@RequestParam(name = "username") String username,
                                                      @RequestParam(name = "receiptPath") String receiptPath) {
        byte[] bytes = membershipService.downloadUserReceipt(username, receiptPath);
        return ResponseEntity.ok()
                .contentLength(bytes.length)
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    @GetMapping("/api/memberships/profile")
    public ProfileDto getCurrentUserProfileData() {
        return membershipService.getCurrentUserProfileData();
    }

    @GetMapping("/api/memberships/profile/transactions/list")
    public List<MonthlyTransactionDto> listCurrentUserTransactions() {
        return membershipService.listCurrentUserTransactions();
    }

    @PostMapping("/api/memberships/profile/transactions/download")
    public ResponseEntity<byte[]> downloadCurrentUserReceipt(@RequestBody String receiptPath) {
        byte[] bytes = membershipService.downloadCurrentUserReceipt(receiptPath);
        return ResponseEntity.ok()
                .contentLength(bytes.length)
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

}
