package com.deac.features.membership.service.impl;

import com.deac.exception.MyException;
import com.deac.features.membership.dto.MembershipEntryInfoDto;
import com.deac.features.membership.dto.ProfileDto;
import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.features.membership.persistence.repository.MembershipRepository;
import com.deac.features.membership.service.MembershipService;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MembershipServiceImpl implements MembershipService {

    private final MembershipRepository membershipRepository;

    private final UserService userService;

    @Autowired
    public MembershipServiceImpl(MembershipRepository membershipRepository, UserService userService) {
        this.membershipRepository = membershipRepository;
        this.userService = userService;
    }

    @Override
    public String toggleUserEnabled(String username, boolean isEnabled) {
        userService.setEnabled(username, isEnabled);
        return "User account status: " + isEnabled;
    }

    @Override
    public String toggleApproved(String username, boolean isApproved) {
        MembershipEntry membershipEntry = membershipRepository.findByUsername(username).orElseThrow(() -> new MyException("Membership does not exist", HttpStatus.BAD_REQUEST));
        membershipEntry.setApproved(isApproved);
        membershipRepository.save(membershipEntry);
        return "Monthly transaction approval status: " + isApproved;
    }

    @Override
    public String toggleHasPaidMembershipFee(String username, boolean hasPaidMembershipFee) {
        MembershipEntry membershipEntry = membershipRepository.findByUsername(username).orElseThrow(() -> new MyException("Membership does not exist", HttpStatus.BAD_REQUEST));
        membershipEntry.setHasPaidMembershipFee(hasPaidMembershipFee);
        membershipRepository.save(membershipEntry);
        return "Membership fee paid: " + hasPaidMembershipFee;
    }

    @Override
    public List<MembershipEntryInfoDto> listMembershipEntries(int pageNumber, int pageSize) {
        Pageable sortedByUsername = PageRequest.of(pageNumber - 1, pageSize, Sort.by("user.username"));
        List<MembershipEntry> membershipEntries = membershipRepository.findBy(sortedByUsername);
        return membershipEntryListToMembershipEntryInfoDtoList(membershipEntries);
    }

    private List<MembershipEntryInfoDto> membershipEntryListToMembershipEntryInfoDtoList(List<MembershipEntry> membershipEntries) {
        return membershipEntries
                .stream()
                .map(membershipEntry -> {
                    User user = membershipEntry.getUser();
                    return new MembershipEntryInfoDto(
                            user.getUsername(),
                            membershipEntry.getMemberSince(),
                            membershipEntry.isHasPaidMembershipFee(),
                            membershipEntry.getMonthlyTransactionReceiptPath(),
                            user.isEnabled(),
                            membershipEntry.isApproved()
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    public long getNumberOfMemberships() {
        return membershipRepository.count();
    }

    @Override
    public ProfileDto getProfileData() {
        String username = userService.getCurrentUsername();
        MembershipEntry membershipEntry = membershipRepository.findByUsername(username).orElseThrow(() -> new MyException("Membership does not exist", HttpStatus.BAD_REQUEST));
        return new ProfileDto(
                username,
                membershipEntry.getUser().getEmail(),
                membershipEntry.getMemberSince(),
                membershipEntry.isHasPaidMembershipFee(),
                membershipEntry.getMonthlyTransactionReceiptPath(),
                membershipEntry.isApproved()
        );
    }

}
