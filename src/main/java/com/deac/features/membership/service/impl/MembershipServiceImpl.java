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
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;

import javax.annotation.PostConstruct;
import javax.mail.MessagingException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MembershipServiceImpl implements MembershipService {

    private final MembershipRepository membershipRepository;

    private final UserService userService;

    private final EmailService emailService;

    private final String receiptsBaseDirectory;

    private String membershipReminderTemplate;

    @Autowired
    public MembershipServiceImpl(MembershipRepository membershipRepository, UserService userService, EmailService emailService, Environment environment) {
        this.membershipRepository = membershipRepository;
        this.userService = userService;
        this.emailService = emailService;
        receiptsBaseDirectory = Objects.requireNonNull(environment.getProperty("file.receipts.rootdir", String.class));
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource emailTemplateResource = resourceLoader.getResource("classpath:templates/EmailTemplate.html");
        Resource membershipReminderTemplateResource = resourceLoader.getResource("classpath:templates/MembershipReminderTemplate.html");
        try (
                Reader emailTemplateReader = new InputStreamReader(emailTemplateResource.getInputStream(), StandardCharsets.UTF_8);
                Reader membershipReminderTemplateReader = new InputStreamReader(membershipReminderTemplateResource.getInputStream(), StandardCharsets.UTF_8)
        ) {
            String emailTemplate = FileCopyUtils.copyToString(emailTemplateReader);
            membershipReminderTemplate = emailTemplate.replace("[BODY_TEMPLATE]", FileCopyUtils.copyToString(membershipReminderTemplateReader));
        } catch (IOException e) {
            e.printStackTrace();
        }
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
    @Transactional
    public List<MembershipEntryInfoDto> listMembershipEntries(int pageNumber, int pageSize, Boolean filterHasPaid) {
        Pageable pageable = PageRequest.of(pageNumber - 1, pageSize, Sort.by("user.username"));
        List<MembershipEntry> membershipEntries;
        if (filterHasPaid == null) {
            membershipEntries = membershipRepository.findBy(pageable);
        } else {
            membershipEntries = membershipRepository.findByHasPaidMembershipFee(filterHasPaid, pageable);
        }
        if (!membershipEntries.isEmpty()) {
            membershipEntries = membershipRepository.findDistinctByIdIn(membershipEntries.stream().map(MembershipEntry::getId).collect(Collectors.toList()));
        }
        return membershipEntryListToMembershipEntryInfoDtoList(membershipEntries);
    }

    public List<MembershipEntryInfoDto> membershipEntryListToMembershipEntryInfoDtoList(List<MembershipEntry> membershipEntries) {
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
        Optional<MembershipEntry> membershipEntryOptional = membershipRepository.findByUsernameFetch(searchTerm);
        if (membershipEntryOptional.isEmpty()) {
            membershipEntryOptional = membershipRepository.findByEmailFetch(searchTerm);
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
    @Transactional
    public UserProfileDto getUserProfileData(String username) {
        User user = userService.getUserByUsername(username);
        MembershipEntry membershipEntry = user.getMembershipEntry();
        return new UserProfileDto(
                user.getSurname() + " " + user.getLastname(),
                user.getUsername(),
                user.getEmail(),
                membershipEntry.getMemberSince(),
                user.isEnabled(),
                user.isVerified(),
                membershipEntry.isHasPaidMembershipFee(),
                membershipEntry.isApproved()
        );
    }

    @Override
    @Transactional
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
    @Transactional
    public ProfileDto getCurrentUserProfileData() {
        User user = userService.getCurrentUser();
        MembershipEntry membershipEntry = user.getMembershipEntry();
        return new ProfileDto(
                user.getSurname() + " " + user.getLastname(),
                user.getUsername(),
                user.getEmail(),
                membershipEntry.getMemberSince(),
                membershipEntry.isHasPaidMembershipFee(),
                membershipEntry.isApproved()
        );
    }

    @Override
    @Transactional
    public List<MonthlyTransactionDto> listCurrentUserTransactions() {
        User currentUser = userService.getCurrentUser();
        return monthlyTransactionListToMonthlyTransactionDtoList(currentUser.getMembershipEntry().getMonthlyTransactions().values());
    }

    private List<MonthlyTransactionDto> monthlyTransactionListToMonthlyTransactionDtoList(Collection<MonthlyTransaction> monthlyTransactions) {
        return monthlyTransactions
                .stream()
                .map(monthlyTransaction -> {
                    Date date = Date.from(monthlyTransaction.getMonthlyTransactionReceiptMonth().atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
                    return new MonthlyTransactionDto(
                            date,
                            monthlyTransaction.getMonthlyTransactionReceiptPath());
                })
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

    @Scheduled(cron = "0 0 0 1 * *")
    @PostConstruct
    public void validateMonthlyTransactions() {
        doValidateMemberships();
    }

    @Scheduled(cron = "0 0 12 20 * *")
    public void monthlyTransactionReminder() {
        checkCurrentMonthTransactions();
    }

    @Transactional
    public void doValidateMemberships() {
        Thread databaseThread = new Thread(() -> {
            List<User> usersToBan = new ArrayList<>();
            List<MembershipEntry> membershipEntries = membershipRepository.findAllActiveMemberships()
                    .stream()
                    .filter(membershipEntry -> !membershipEntry.getUser().getRoles().contains(Role.ADMIN))
                    .peek(membershipEntry -> {
                        Map<String, MonthlyTransaction> originalMonthlyTransactions = membershipEntry.getMonthlyTransactions();
                        boolean unpaid = originalMonthlyTransactions.entrySet()
                                .stream()
                                .filter(entry -> entry.getValue().getMonthlyTransactionReceiptMonth().isBefore(YearMonth.now().minusMonths(2L)))
                                .anyMatch(entry -> entry.getValue().getMonthlyTransactionReceiptPath() == null);
                        if (unpaid) {
                            membershipEntry.setHasPaidMembershipFee(false);
                            membershipEntry.setApproved(false);
                            usersToBan.add(membershipEntry.getUser());
                        } else {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM");
                            boolean isNewMonth = false;
                            if (!originalMonthlyTransactions.containsKey(YearMonth.now().format(formatter))) {
                                originalMonthlyTransactions.put(YearMonth.now().format(formatter), new MonthlyTransaction(YearMonth.now(), null));
                                isNewMonth = true;
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
                            if (isNewMonth) {
                                membershipEntry.setHasPaidMembershipFee(false);
                                membershipEntry.setApproved(false);
                            }
                        }
                    })
                    .collect(Collectors.toList());
            if (!usersToBan.isEmpty()) {
                userService.banUsers(usersToBan);
            }
            for (List<MembershipEntry> membershipEntryListChunk : ListUtils.partition(membershipEntries, 1000)) {
                membershipRepository.updateInBatch(membershipEntryListChunk);
            }
        });
        databaseThread.setName("membership-thread");
        databaseThread.start();
    }

    @Transactional
    public void checkCurrentMonthTransactions() {
        Thread membershipMailThread = new Thread(() -> membershipRepository.findAllUnpaidMemberships()
                .stream()
                .filter(membershipEntry -> !membershipEntry.getUser().getRoles().contains(Role.ADMIN))
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
                    unpaidMonthsString.append("<ul>");
                    for (String unpaidMonth : unpaidMonths) {
                        unpaidMonthsString.append("<li>").append(unpaidMonth).append("</li>");
                    }
                    unpaidMonthsString.append("</ul>");
                    try {
                        sendMembershipEmail(membershipEntry.getUser(), unpaidMonths.size(), unpaidMonthsString.toString());
                    } catch (MessagingException ignored) {
                    }
                }));
        membershipMailThread.setName("membership-mail-thread");
        membershipMailThread.start();
    }

    private void sendMembershipEmail(User user, Integer monthCount, String unpaidMonthsString) throws MessagingException {
        String subject = "";
        String line1 = "";
        String line2 = "";
        String line3 = "";
        String line4 = "";
        String line5 = "";
        String line6 = "";
        String line7 = "";
        switch (user.getLanguage()) {
            case HU:
                subject = "Havi emlékeztető a tagdíj befizetésére";
                line1 = "Tisztelt [SURNAME] [LASTNAME],";
                line2 = "ez a havi automatizált email-je, amiben emlékeztetni szereténk a tagdíja befizetésére.";
                line3 = "Önnek jelenleg [MONTH_COUNT] fizetetlen hónapja van:";
                line4 = "Szeretnénk emlékeztetni, hogy amennyiben több mint 3 hónapon keresztül nem fizeti be a tagdíjat, a tagságát felfüggesztjük weboldalunkon.";
                line5 = "Amennyiben tagsága felfüggesztésre került (ki lett tiltva), de szeretni újra csatlakozni közösségünkbe, vegye fel a kapcsolatot support csapatunkkal.";
                line6 = "Üdvözlettel,";
                line7 = "DEAC Kyokushin Karate Support Csapat";
                break;
            case EN:
                subject = "Monthly reminder to pay your membership fee";
                line1 = "Dear [SURNAME] [LASTNAME],";
                line2 = "this is your monthly automated email to remind you to pay your membership fee.";
                line3 = "You currently have [MONTH_COUNT] unpaid month(s):";
                line4 = "Remember that if you do not pay the given fees for more than 3 months, your membership on our website will be suspended.";
                line5 = "In case you get suspended (banned), but you would like to rejoin our site, contact our support.";
                line6 = "Regards,";
                line7 = "DEAC Kyokushin Karate Support Staff";
                break;
            default:
                throw new MyException("Unsupported language", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        line1 = line1.replace("[SURNAME]", user.getSurname()).replace("[LASTNAME]", user.getLastname());
        line3 = line3.replace("[MONTH_COUNT]", String.valueOf(monthCount));
        String emailBody = membershipReminderTemplate
                .replace("[LINE_1]", line1)
                .replace("[LINE_2]", line2)
                .replace("[LINE_3]", line3)
                .replace("[UNPAID_MONTHS]", unpaidMonthsString)
                .replace("[LINE_4]", line4)
                .replace("[LINE_5]", line5)
                .replace("[LINE_6]", line6)
                .replace("[LINE_7]", line7);
        emailService.sendMessage(user.getEmail(),
                subject,
                emailBody,
                List.of());
    }

}
