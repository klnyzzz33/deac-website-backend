package com.deac.features.membership.service.impl;

import com.deac.exception.MyException;
import com.deac.features.membership.dto.MembershipEntryInfoDto;
import com.deac.features.membership.dto.MonthlyTransactionDto;
import com.deac.features.membership.dto.ProfileDto;
import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.features.membership.persistence.entity.MonthlyTransaction;
import com.deac.features.membership.persistence.repository.MembershipRepository;
import com.deac.features.membership.service.MembershipService;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class MembershipServiceImpl implements MembershipService {

    private final MembershipRepository membershipRepository;

    private final UserService userService;

    private final String receiptsBaseDirectory;

    @Autowired
    public MembershipServiceImpl(MembershipRepository membershipRepository, UserService userService, Environment environment) {
        this.membershipRepository = membershipRepository;
        this.userService = userService;
        receiptsBaseDirectory = Objects.requireNonNull(environment.getProperty("file.receipts.rootdir", String.class));
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
                membershipEntry.isApproved()
        );
    }

    @Override
    public List<MonthlyTransactionDto> listCurrentUserTransactions() {
        User currentUser = userService.getCurrentUser();
        return monthlyTransactionListToMonthlyTransactionDtoList(currentUser.getMembershipEntry().getMonthlyTransactions());
    }

    private List<MonthlyTransactionDto> monthlyTransactionListToMonthlyTransactionDtoList(List<MonthlyTransaction> monthlyTransactions) {
        return monthlyTransactions
                .stream()
                .map(monthlyTransaction -> new MonthlyTransactionDto(monthlyTransaction.getMonthlyTransactionReceiptMonth(), monthlyTransaction.getMonthlyTransactionReceiptPath()))
                .sorted(Comparator.comparing(MonthlyTransactionDto::getYearMonth).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public byte[] downloadCurrentUserReceipt(String receiptPath) {
        try {
            String baseDir = receiptsBaseDirectory + "user_" + userService.getCurrentUserId() + "/";
            String targetPath = baseDir + receiptPath;
            return Files.readAllBytes(Path.of(targetPath));
        } catch (IOException e) {
            throw new MyException("Could not download receipt", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
