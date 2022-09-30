package com.deac.features.payment.service.general.impl;

import com.deac.exception.MyException;
import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.features.payment.dto.CheckoutInfoDto;
import com.deac.features.payment.dto.CheckoutItemDto;
import com.deac.features.payment.dto.ManualPaymentItemDto;
import com.deac.features.payment.dto.ManualPaymentSaveDto;
import com.deac.features.payment.persistence.entity.MonthlyTransaction;
import com.deac.features.payment.service.general.PaymentService;
import com.deac.mail.Attachment;
import com.deac.mail.EmailService;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.UserService;
import com.stripe.model.PaymentMethod;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static com.deac.features.payment.service.stripe.impl.StripePaymentServiceImpl.retrievePaymentMethod;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final UserService userService;

    private final EmailService emailService;

    private final Long amount;

    private final String currency;

    private final String receiptsBaseDirectory;

    @Autowired
    public PaymentServiceImpl(UserService userService, EmailService emailService, Environment environment) {
        this.userService = userService;
        this.emailService = emailService;
        amount = Objects.requireNonNull(environment.getProperty("membership.amount", Long.class));
        currency = Objects.requireNonNull(environment.getProperty("membership.currency", String.class));
        receiptsBaseDirectory = Objects.requireNonNull(environment.getProperty("file.receipts.rootdir", String.class));
    }

    @Override
    public String getCurrency() {
        return currency;
    }

    @Override
    @SuppressWarnings(value = "DuplicatedCode")
    public String savePaymentManual(ManualPaymentSaveDto payment) {
        try {
            User user = userService.getUserByUsername(payment.getUsername());
            MembershipEntry userMembershipEntry = user.getMembershipEntry();
            Map<String, MonthlyTransaction> monthlyTransactions = userMembershipEntry.getMonthlyTransactions();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM");
            long totalAmount = 0;
            Comparator<String> comparator = Comparator.comparing(YearMonth::parse);
            SortedMap<String, String> items = new TreeMap<>(comparator.reversed());
            Set<YearMonth> uniqueElements = new HashSet<>();
            for (ManualPaymentItemDto item : payment.getItems()) {
                if (item.getAmount() <= 0) {
                    throw new MyException("Amount must be non-negative", HttpStatus.BAD_REQUEST);
                }
                totalAmount += item.getAmount();
                if (!uniqueElements.add(item.getMonth())) {
                    throw new MyException("Duplicate month detected", HttpStatus.BAD_REQUEST);
                }
                if (item.getMonth().isAfter(YearMonth.now()) || item.getMonth().isBefore(YearMonth.now().minusYears(1L))) {
                    throw new MyException("Invalid month", HttpStatus.BAD_REQUEST);
                }
                String month = item.getMonth().format(formatter);
                if (!monthlyTransactions.containsKey(month)) {
                    monthlyTransactions.put(month, new MonthlyTransaction(item.getMonth(), null));
                } else if (monthlyTransactions.get(month).getMonthlyTransactionReceiptPath() != null) {
                    throw new MyException("Month already paid", HttpStatus.BAD_REQUEST);
                }
                items.put(item.getMonth().toString(), item.getAmount().toString());
            }
            totalAmount *= 100;
            Attachment savedFileInfo = generatePaymentReceipt("Weboldalon kívül fizetve", "Weboldalon kívül fizetve", totalAmount, items, user);
            for (Map.Entry<String, String> itemEntry : items.entrySet()) {
                String yearMonth = YearMonth.parse(itemEntry.getKey()).format(formatter);
                monthlyTransactions.get(yearMonth).setMonthlyTransactionReceiptPath(savedFileInfo.getName());
            }
            user.setMembershipEntry(userMembershipEntry);
            List<MonthlyTransaction> unPaidMonths = userMembershipEntry.getMonthlyTransactions().values()
                    .stream()
                    .filter(monthlyTransaction -> monthlyTransaction.getMonthlyTransactionReceiptPath() == null)
                    .collect(Collectors.toList());
            if (unPaidMonths.isEmpty()) {
                userMembershipEntry.setHasPaidMembershipFee(true);
            }
            userService.saveUser(user);
            return "Payment successfully saved";
        } catch (Exception e) {
            throw new MyException("Could not save payment", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CheckoutInfoDto listCheckoutInfo() {
        MembershipEntry currentUserMembershipEntry = checkIfMembershipAlreadyPaid();
        boolean isHuf = "huf".equals(currency);
        List<CheckoutItemDto> items;
        if (isHuf) {
            items = currentUserMembershipEntry.getMonthlyTransactions().values()
                    .stream()
                    .filter(monthlyTransaction -> monthlyTransaction.getMonthlyTransactionReceiptPath() == null)
                    .map(monthlyTransaction -> new CheckoutItemDto(monthlyTransaction.getMonthlyTransactionReceiptMonth(), amount / 100))
                    .sorted(Comparator.comparing(CheckoutItemDto::getMonthlyTransactionReceiptMonth).reversed())
                    .collect(Collectors.toList());
        } else {
            items = currentUserMembershipEntry.getMonthlyTransactions().values()
                    .stream()
                    .filter(monthlyTransaction -> monthlyTransaction.getMonthlyTransactionReceiptPath() == null)
                    .map(monthlyTransaction -> new CheckoutItemDto(monthlyTransaction.getMonthlyTransactionReceiptMonth(), amount))
                    .sorted(Comparator.comparing(CheckoutItemDto::getMonthlyTransactionReceiptMonth).reversed())
                    .collect(Collectors.toList());
        }
        return new CheckoutInfoDto(items, currency);
    }

    @Override
    public MembershipEntry checkIfMembershipAlreadyPaid() {
        User currentUser = userService.getCurrentUser();
        MembershipEntry currentUserMembershipEntry = currentUser.getMembershipEntry();
        List<MonthlyTransaction> monthlyTransactions = currentUserMembershipEntry.getMonthlyTransactions().values()
                .stream()
                .filter(monthlyTransaction -> monthlyTransaction.getMonthlyTransactionReceiptPath() == null)
                .collect(Collectors.toList());
        if (monthlyTransactions.isEmpty()) {
            throw new MyException("Monthly membership fee already paid", HttpStatus.BAD_REQUEST);
        }
        return currentUserMembershipEntry;
    }

    @Override
    @SuppressWarnings("DuplicatedCode")
    public Attachment generatePaymentReceipt(String paymentId, String paymentMethodId, Long totalAmount, Map<String, String> items, User user) {
        try {
            PDDocument pdf = new PDDocument();
            PDPage page = new PDPage();
            pdf.addPage(page);
            float margin = 50;
            PDFont fontNormal = PDType0Font.load(pdf, new ClassPathResource("fonts/Montserrat-Regular.ttf").getFile());
            PDFont fontBold = PDType0Font.load(pdf, new ClassPathResource("fonts/Montserrat-Bold.ttf").getFile());
            String receiptId = UUID.randomUUID().toString();
            PDPageContentStream contentStream = new PDPageContentStream(pdf, page);
            String text;
            float textWidth;
            float textHeight;
            float currentLineHeightPosition;

            contentStream.beginText();
            contentStream.setFont(fontBold, 22);
            contentStream.setNonStrokingColor(Color.BLACK);
            text = "Számla";
            currentLineHeightPosition = page.getMediaBox().getHeight() - margin;
            contentStream.newLineAtOffset(margin, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontBold, 22);
            contentStream.setNonStrokingColor(Color.GRAY);
            text = "DEAC Kyokushin Karate";
            textWidth = fontBold.getStringWidth(text) / 1000 * 22;
            textHeight = fontBold.getFontDescriptor().getFontBoundingBox().getHeight() / 1000 * 22;
            contentStream.newLineAtOffset(page.getMediaBox().getWidth() - margin - textWidth, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontBold, 13);
            contentStream.setNonStrokingColor(Color.BLACK);
            text = "Számla azonosító:";
            currentLineHeightPosition = currentLineHeightPosition - textHeight - 25;
            contentStream.newLineAtOffset(margin, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontNormal, 13);
            text = receiptId;
            textWidth = fontNormal.getStringWidth(text) / 1000 * 13;
            textHeight = fontNormal.getFontDescriptor().getFontBoundingBox().getHeight() / 1000 * 13;
            contentStream.newLineAtOffset(page.getMediaBox().getWidth() - margin - textWidth, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontBold, 13);
            text = "Számla kiállításának dátuma:";
            currentLineHeightPosition = currentLineHeightPosition - textHeight - 10;
            contentStream.newLineAtOffset(margin, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontNormal, 13);
            text = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            textWidth = fontNormal.getStringWidth(text) / 1000 * 13;
            contentStream.newLineAtOffset(page.getMediaBox().getWidth() - margin - textWidth, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontBold, 13);
            text = "Számla befizetője";
            textWidth = fontBold.getStringWidth(text) / 1000 * 13;
            currentLineHeightPosition = currentLineHeightPosition - textHeight - 10;
            contentStream.newLineAtOffset(margin, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontBold, 13);
            text = "---";
            contentStream.newLineAtOffset(margin + textWidth + 10, currentLineHeightPosition);
            textWidth = margin + textWidth + 10 + fontBold.getStringWidth(text) / 1000 * 13;
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontBold, 13);
            contentStream.newLineAtOffset(textWidth + 10, currentLineHeightPosition);
            text = "név:";
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontNormal, 13);
            text = user.getSurname() + " " + user.getLastname();
            contentStream.newLineAtOffset(page.getMediaBox().getWidth() - margin - fontNormal.getStringWidth(text) / 1000 * 13, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontBold, 13);
            currentLineHeightPosition = currentLineHeightPosition - textHeight - 10;
            contentStream.newLineAtOffset(textWidth + 10, currentLineHeightPosition);
            text = "azonosító:";
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontNormal, 13);
            text = "USER-" + user.getId();
            contentStream.newLineAtOffset(page.getMediaBox().getWidth() - margin - fontNormal.getStringWidth(text) / 1000 * 13, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontBold, 13);
            currentLineHeightPosition = currentLineHeightPosition - textHeight - 10;
            contentStream.newLineAtOffset(textWidth + 10, currentLineHeightPosition);
            text = "email:";
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontNormal, 13);
            text = user.getEmail();
            contentStream.newLineAtOffset(page.getMediaBox().getWidth() - margin - fontNormal.getStringWidth(text) / 1000 * 13, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontBold, 17);
            text = "ÖSSZEGZÉS";
            currentLineHeightPosition = currentLineHeightPosition - textHeight - 35;
            contentStream.newLineAtOffset(margin, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontBold, 13);
            text = "Tranzakció azonosító:";
            float maxWidth = Math.max(
                    fontBold.getStringWidth("Tranzakció azonosító:") / 1000 * 13,
                    Math.max(
                            fontBold.getStringWidth("Fizetési mód:") / 1000 * 13,
                            Math.max(
                                    fontBold.getStringWidth("Összeg:") / 1000 * 13,
                                    20 + fontNormal.getStringWidth("____.__. havi tagdíj") / 1000 * 13
                            )
                    )
            );
            currentLineHeightPosition = currentLineHeightPosition - textHeight - 15;
            contentStream.newLineAtOffset(margin, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontNormal, 13);
            text = paymentId;
            contentStream.newLineAtOffset(margin + maxWidth + 20, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontBold, 13);
            text = "Fizetési mód:";
            currentLineHeightPosition = currentLineHeightPosition - textHeight - 10;
            contentStream.newLineAtOffset(margin, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontNormal, 13);
            text = "---";
            switch (paymentMethodId) {
                case "PayPal":
                    text = paymentMethodId;
                    break;
                case "Weboldalon kívül fizetve":
                    break;
                default:
                    PaymentMethod paymentMethod = retrievePaymentMethod(paymentMethodId);
                    String cardBrand;
                    String last4;
                    if (paymentMethod != null) {
                        cardBrand = paymentMethod.getCard().getBrand();
                        last4 = paymentMethod.getCard().getLast4();
                        text = cardBrand.toUpperCase() + " - " + last4;
                    }
            }
            contentStream.newLineAtOffset(margin + maxWidth + 20, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontBold, 13);
            text = "Termékek:";
            currentLineHeightPosition = currentLineHeightPosition - textHeight - 10;
            contentStream.newLineAtOffset(margin, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            for (Map.Entry<String, String> itemEntry : items.entrySet()) {
                String productName = YearMonth.parse(itemEntry.getKey()).format(DateTimeFormatter.ofPattern("yyyy.MM."));
                String productPrice = itemEntry.getValue();

                contentStream.beginText();
                contentStream.setFont(fontNormal, 13);
                text = productName + " havi tagdíj";
                currentLineHeightPosition = currentLineHeightPosition - textHeight - 10;
                contentStream.newLineAtOffset(margin + 20, currentLineHeightPosition);
                contentStream.showText(text);
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(fontNormal, 13);
                if ("huf".equals(currency)) {
                    text = productPrice + ".00 Ft";
                } else {
                    text = productPrice + ".00";
                }
                contentStream.newLineAtOffset(margin + maxWidth + 20, currentLineHeightPosition);
                contentStream.showText(text);
                contentStream.endText();
            }

            contentStream.beginText();
            contentStream.setFont(fontBold, 13);
            text = "Összesen:";
            currentLineHeightPosition = currentLineHeightPosition - textHeight - 10;
            contentStream.newLineAtOffset(margin, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontNormal, 13);
            if ("huf".equals(currency)) {
                text = totalAmount / 100 + ".00 Ft";
            } else {
                text = totalAmount.toString() + ".00";
            }
            contentStream.newLineAtOffset(margin + maxWidth + 20, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontBold, 15);
            text = "DEAC Kyokushin Karate";
            textWidth = fontBold.getStringWidth(text) / 1000 * 15;
            currentLineHeightPosition = currentLineHeightPosition - textHeight - 70;
            contentStream.newLineAtOffset(page.getMediaBox().getWidth() - margin - textWidth, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontNormal, 13);
            text = "+36307297040";
            textWidth = fontNormal.getStringWidth(text) / 1000 * 13;
            currentLineHeightPosition = currentLineHeightPosition - textHeight - 10;
            contentStream.newLineAtOffset(page.getMediaBox().getWidth() - margin - textWidth, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(fontNormal, 13);
            text = "deackyokushindev@gmail.com";
            textWidth = fontNormal.getStringWidth(text) / 1000 * 13;
            currentLineHeightPosition = currentLineHeightPosition - textHeight - 10;
            contentStream.newLineAtOffset(page.getMediaBox().getWidth() - margin - textWidth, currentLineHeightPosition);
            contentStream.showText(text);
            contentStream.endText();
            String baseDir = receiptsBaseDirectory + "user_" + user.getId() + "/";
            String filePath = "receipt_" + receiptId + ".pdf";
            Files.createDirectories(Path.of(baseDir));
            String targetPath = baseDir + filePath;
            contentStream.close();
            pdf.save(targetPath);
            pdf.close();
            Path path = Path.of(targetPath);
            byte[] savedData = Files.readAllBytes(path);
            return new Attachment(filePath, savedData);
        } catch (IOException e) {
            throw new MyException("Could not generate receipt", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void sendPaymentSuccessEmail(User user, List<String> items, List<Attachment> attachments) {
        StringBuilder paidMonthsString = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            paidMonthsString.append(items.get(i));
            if (i != items.size() - 1) {
                paidMonthsString.append(", ");
            } else {
                paidMonthsString.append(".<br>");
            }
        }
        try {
            emailService.sendMessage(user.getEmail(),
                    "Your payment receipt",
                    "<h3>Dear " + user.getSurname() + " " + user.getLastname() + ", you have successfully paid the membership fees for the following months:<br>" + paidMonthsString + "We've sent your payment receipt as an attachment.<h3>",
                    attachments);
        } catch (MessagingException ignored) {
        }
    }

}
