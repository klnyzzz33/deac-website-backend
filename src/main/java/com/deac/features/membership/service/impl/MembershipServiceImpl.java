package com.deac.features.membership.service.impl;

import com.deac.exception.MyException;
import com.deac.features.membership.dto.MembershipEntryInfoDto;
import com.deac.features.membership.dto.MonthlyTransactionDto;
import com.deac.features.membership.dto.ProfileDto;
import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.features.membership.persistence.entity.MonthlyTransaction;
import com.deac.features.membership.persistence.repository.MembershipRepository;
import com.deac.features.membership.service.MembershipService;
import com.deac.user.persistence.entity.Role;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
        /*MembershipEntry entry = userService.getCurrentUser().getMembershipEntry();
        Map<String, MonthlyTransaction> monthlyTransactions = entry.getMonthlyTransactions();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM");
        monthlyTransactions.put(YearMonth.now().minusMonths(1L).format(formatter), new MonthlyTransaction(YearMonth.now().minusMonths(1L), null));
        monthlyTransactions.put(YearMonth.now().minusMonths(2L).format(formatter), new MonthlyTransaction(YearMonth.now().minusMonths(2L), null));
        monthlyTransactions.put(YearMonth.now().minusMonths(3L).format(formatter), new MonthlyTransaction(YearMonth.now().minusMonths(3L), null));
        membershipRepository.save(entry);*/


        User user = userService.getCurrentUser();
        MembershipEntry membershipEntry = user.getMembershipEntry();
        return new ProfileDto(
                user.getSurname() + user.getLastname(),
                user.getUsername(),
                membershipEntry.getUser().getEmail(),
                membershipEntry.getMemberSince(),
                membershipEntry.isHasPaidMembershipFee(),
                membershipEntry.isApproved()
        );
    }

    @Override
    public List<MonthlyTransactionDto> listCurrentUserTransactions() {
        User currentUser = userService.getCurrentUser();
        return monthlyTransactionListToMonthlyTransactionDtoList(currentUser.getMembershipEntry().getMonthlyTransactions().values());
    }

    private List<MonthlyTransactionDto> monthlyTransactionListToMonthlyTransactionDtoList(Collection<MonthlyTransaction> monthlyTransactions) {
        return monthlyTransactions
                .stream()
                .map(monthlyTransaction -> new MonthlyTransactionDto(monthlyTransaction.getMonthlyTransactionReceiptMonth(), monthlyTransaction.getMonthlyTransactionReceiptPath()))
                .sorted(Comparator.comparing(MonthlyTransactionDto::getYearMonth).reversed())
                .limit(12L)
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

    @PostConstruct
    public void validateMonthlyTransactionsOnStartup() {
        doValidate(true);
    }

    @Scheduled(cron = "0 0 0 1 * *")
    public void validateMonthlyTransactions() {
        doValidate(false);
    }

    @Transactional
    public void doValidate(boolean onStartup) {
        List<MembershipEntry> membershipEntries = membershipRepository.findAll()
                .stream()
                .filter(membershipEntry -> !membershipEntry.getUser().getRoles().contains(Role.ADMIN))
                .peek(membershipEntry -> {
                    if (!onStartup) {
                        membershipEntry.setHasPaidMembershipFee(false);
                        membershipEntry.setApproved(false);
                    }
                    if (membershipEntry.getUser().isEnabled()) {
                        Map<String, MonthlyTransaction> originalMonthlyTransactions = membershipEntry.getMonthlyTransactions();
                        long unpaidMonths = originalMonthlyTransactions.entrySet()
                                .stream()
                                .filter(entry -> !YearMonth.now().equals(entry.getValue().getMonthlyTransactionReceiptMonth()))
                                .filter(entry -> entry.getValue().getMonthlyTransactionReceiptPath() == null)
                                .count();
                        if (unpaidMonths >= 3L) {
                            if (!onStartup) {
                                userService.setEnabled(membershipEntry.getUser().getUsername(), false);
                            }
                        } else {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM");
                            if (!originalMonthlyTransactions.containsKey(YearMonth.now().format(formatter))) {
                                originalMonthlyTransactions.put(YearMonth.now().format(formatter), new MonthlyTransaction(YearMonth.now(), null));
                            }
                            Map<String, MonthlyTransaction> monthlyTransactions = originalMonthlyTransactions.entrySet()
                                    .stream()
                                    .filter(entry -> entry.getValue().getMonthlyTransactionReceiptMonth().isAfter(YearMonth.now().minusYears(1L)))
                                    .collect(Collectors.toMap(
                                            Map.Entry::getKey,
                                            Map.Entry::getValue
                                    ));
                            originalMonthlyTransactions.clear();
                            originalMonthlyTransactions.putAll(monthlyTransactions);
                        }
                    }
                })
                .collect(Collectors.toList());
        membershipRepository.saveAll(membershipEntries);
    }

}
