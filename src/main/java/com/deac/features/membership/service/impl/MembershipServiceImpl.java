package com.deac.features.membership.service.impl;

import com.deac.exception.MyException;
import com.deac.features.membership.dto.MembershipEntryInfoDto;
import com.deac.features.membership.dto.MonthlyTransactionDto;
import com.deac.features.membership.dto.ProfileDto;
import com.deac.features.membership.dto.UserProfileDto;
import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.features.payment.persistence.entity.MonthlyTransaction;
import com.deac.features.membership.persistence.repository.MembershipRepository;
import com.deac.features.membership.service.MembershipService;
import com.deac.mail.EmailService;
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
import javax.mail.MessagingException;
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

    private final EmailService emailService;

    private final String receiptsBaseDirectory;

    @Autowired
    public MembershipServiceImpl(MembershipRepository membershipRepository, UserService userService, EmailService emailService, Environment environment) {
        this.membershipRepository = membershipRepository;
        this.userService = userService;
        this.emailService = emailService;
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
    public List<MembershipEntryInfoDto> listMembershipEntries(int pageNumber, int pageSize, Boolean filterHasPaid) {
        Pageable pageable = PageRequest.of(pageNumber - 1, pageSize, Sort.by("user.username"));
        List<MembershipEntry> membershipEntries;
        if (filterHasPaid == null) {
            membershipEntries = membershipRepository.findBy(pageable);
        } else {
            membershipEntries = membershipRepository.findByHasPaidMembershipFee(filterHasPaid, pageable);
        }
        return membershipEntryListToMembershipEntryInfoDtoList(membershipEntries);
    }

    private List<MembershipEntryInfoDto> membershipEntryListToMembershipEntryInfoDtoList(List<MembershipEntry> membershipEntries) {
        return membershipEntries
                .stream()
                .filter(membershipEntry -> !membershipEntry.getUser().getRoles().contains(Role.ADMIN))
                .map(membershipEntry -> {
                    User user = membershipEntry.getUser();
                    boolean hasReceipts = membershipEntry.getMonthlyTransactions().values()
                            .stream()
                            .anyMatch(monthlyTransaction -> monthlyTransaction.getMonthlyTransactionReceiptPath() != null);
                    return new MembershipEntryInfoDto(
                            user.getUsername(),
                            membershipEntry.getMemberSince(),
                            membershipEntry.isHasPaidMembershipFee(),
                            user.isEnabled(),
                            membershipEntry.isApproved(),
                            hasReceipts
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    public MembershipEntryInfoDto searchUser(String searchTerm) {
        Optional<MembershipEntry> membershipEntryOptional = membershipRepository.findByUsername(searchTerm);
        if (membershipEntryOptional.isEmpty()) {
            membershipEntryOptional = membershipRepository.findByEmail(searchTerm);
            if (membershipEntryOptional.isEmpty()) {
                return null;
            }
        }
        MembershipEntry membershipEntry = membershipEntryOptional.get();
        User user = membershipEntry.getUser();
        if (user.getRoles().contains(Role.ADMIN)) {
            return null;
        }
        boolean hasReceipts = membershipEntry.getMonthlyTransactions().values()
                .stream()
                .anyMatch(monthlyTransaction -> monthlyTransaction.getMonthlyTransactionReceiptPath() != null);
        return new MembershipEntryInfoDto(
                user.getUsername(),
                membershipEntry.getMemberSince(),
                membershipEntry.isHasPaidMembershipFee(),
                user.isEnabled(),
                membershipEntry.isApproved(),
                hasReceipts
        );
    }

    @Override
    public long getNumberOfMemberships(Boolean filterHasPaid) {
        if (filterHasPaid == null) {
            return membershipRepository.count();
        } else {
            return membershipRepository.countAllByHasPaidMembershipFee(filterHasPaid);
        }
    }

    @Override
    public UserProfileDto getUserProfileData(String username) {
        User user = userService.getUserByUsername(username);
        MembershipEntry membershipEntry = user.getMembershipEntry();
        return new UserProfileDto(
                user.getSurname() + " " + user.getLastname(),
                user.getUsername(),
                membershipEntry.getUser().getEmail(),
                membershipEntry.getMemberSince(),
                user.isEnabled(),
                user.isVerified(),
                membershipEntry.isHasPaidMembershipFee(),
                membershipEntry.isApproved()
        );
    }

    @Override
    public List<MonthlyTransactionDto> listUserTransactions(String username) {
        User user = userService.getUserByUsername(username);
        return monthlyTransactionListToMonthlyTransactionDtoList(user.getMembershipEntry().getMonthlyTransactions().values());
    }

    @Override
    public byte[] downloadUserReceipt(String username, String receiptPath) {
        try {
            String baseDir = receiptsBaseDirectory + "user_" + userService.getUserByUsername(username).getId() + "/";
            String targetPath = baseDir + receiptPath;
            return Files.readAllBytes(Path.of(targetPath));
        } catch (IOException e) {
            e.printStackTrace();
            throw new MyException("Could not download receipt", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ProfileDto getCurrentUserProfileData() {
        User user = userService.getCurrentUser();
        MembershipEntry membershipEntry = user.getMembershipEntry();
        return new ProfileDto(
                user.getSurname() + " " + user.getLastname(),
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

    @Scheduled(cron = "0 0 0 20 * *")
    public void monthlyTransactionReminder() {
        checkCurrentMonthTransactions();
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
                        boolean unpaid = originalMonthlyTransactions.entrySet()
                                .stream()
                                .filter(entry -> entry.getValue().getMonthlyTransactionReceiptMonth().isBefore(YearMonth.now().minusMonths(2L)))
                                .anyMatch(entry -> entry.getValue().getMonthlyTransactionReceiptPath() == null);
                        if (unpaid) {
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

    @Transactional
    public void checkCurrentMonthTransactions() {
        membershipRepository.findAll()
                .stream()
                .filter(membershipEntry -> !membershipEntry.getUser().getRoles().contains(Role.ADMIN))
                .filter(membershipEntry -> !membershipEntry.isHasPaidMembershipFee())
                .forEach(membershipEntry -> {
                    Map<String, MonthlyTransaction> monthlyTransactions = membershipEntry.getMonthlyTransactions();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM");
                    Comparator<String> comparator = Comparator.comparing((String s) -> YearMonth.parse(s, formatter));
                    List<String> unpaidMonths = monthlyTransactions.entrySet()
                            .stream()
                            .filter(entry -> entry.getValue().getMonthlyTransactionReceiptPath() == null)
                            .map(Map.Entry::getKey)
                            .sorted(comparator.reversed())
                            .collect(Collectors.toList());
                    StringBuilder unpaidMonthsString = new StringBuilder();
                    for (int i = 0; i < unpaidMonths.size(); i++) {
                        unpaidMonthsString.append(unpaidMonths.get(i));
                        if (i != unpaidMonths.size() - 1) {
                            unpaidMonthsString.append(", ");
                        } else {
                            unpaidMonthsString.append(".<br>");
                        }
                    }
                    try {
                        emailService.sendMessage(membershipEntry.getUser().getEmail(),
                                "Monthly reminder to pay your membership fee",
                                "<h3>Dear " + membershipEntry.getUser().getSurname() + " " + membershipEntry.getUser().getLastname() + ", this is your monthly automated email to remind you to pay your membership fee. You currently have " + unpaidMonths.size() + " unpaid month(s):<br>" + unpaidMonthsString + "Remember that if you do not pay the given fees for more than 3 months, you will be banned from the site.<br>In case you get banned but you would like to rejoin the site, contact our support.<h3>");
                    } catch (MessagingException ignored) {
                    }
                });
    }

}
