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
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;

import javax.mail.MessagingException;
import java.awt.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
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

    private String paymentTemplate;

    @Autowired
    public PaymentServiceImpl(UserService userService, EmailService emailService, Environment environment) {
        this.userService = userService;
        this.emailService = emailService;
        amount = Objects.requireNonNull(environment.getProperty("membership.amount", Long.class));
        currency = Objects.requireNonNull(environment.getProperty("membership.currency", String.class));
        receiptsBaseDirectory = Objects.requireNonNull(environment.getProperty("file.receipts.rootdir", String.class));
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource emailTemplateResource = resourceLoader.getResource("classpath:templates/EmailTemplate.html");
        Resource paymentTemplateResource = resourceLoader.getResource("classpath:templates/PaymentTemplate.html");
        try (
                Reader emailTemplateReader = new InputStreamReader(emailTemplateResource.getInputStream(), StandardCharsets.UTF_8);
                Reader paymentTemplateReader = new InputStreamReader(paymentTemplateResource.getInputStream(), StandardCharsets.UTF_8)
        ) {
            String emailTemplate = FileCopyUtils.copyToString(emailTemplateReader);
            paymentTemplate = emailTemplate.replace("[BODY_TEMPLATE]", FileCopyUtils.copyToString(paymentTemplateReader));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getCurrency() {
        return currency;
    }

    @Override
    @Transactional
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
                String productPrice = itemEntry.getValue();
                MonthlyTransaction monthlyTransaction = monthlyTransactions.get(yearMonth);
                monthlyTransaction.setMonthlyTransactionReceiptPath(savedFileInfo.getName());
                monthlyTransaction.setAmount(Long.valueOf(productPrice));
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
    @Transactional
    public CheckoutInfoDto listCheckoutInfo() {
        User currentUser = userService.getCurrentUser();
        validateOrder(currentUser, null);
        MembershipEntry currentUserMembershipEntry = currentUser.getMembershipEntry();
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
    public void validateOrder(User user, List<CheckoutItemDto> items) {
        MembershipEntry currentUserMembershipEntry = user.getMembershipEntry();
        List<YearMonth> unpaidMonths = currentUserMembershipEntry.getMonthlyTransactions().values()
                .stream()
                .filter(monthlyTransaction -> monthlyTransaction.getMonthlyTransactionReceiptPath() == null)
                .map(MonthlyTransaction::getMonthlyTransactionReceiptMonth)
                .collect(Collectors.toList());
        if (unpaidMonths.isEmpty()) {
            throw new MyException("Monthly membership fee already paid", HttpStatus.BAD_REQUEST);
        }
        if (items != null) {
            boolean invalidItems = items.stream()
                    .anyMatch(checkoutItemDto -> {
                        boolean isHuf = "huf".equals(currency);
                        Long actualAmount = isHuf ? amount / 100 : amount;
                        return !checkoutItemDto.getAmount().equals(actualAmount)
                                || !unpaidMonths.contains(checkoutItemDto.getMonthlyTransactionReceiptMonth());
                    });
            if (invalidItems) {
                throw new MyException("Invalid items specified", HttpStatus.BAD_REQUEST);
            }
        }
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
        paidMonthsString.append("<ul>");
        for (String item : items) {
            paidMonthsString.append("<li>").append(item).append("</li>");
        }
        paidMonthsString.append("</ul>");
        try {
            sendPaymentEmail(user, paidMonthsString.toString(), attachments);
        } catch (MessagingException ignored) {
        }
    }

    private void sendPaymentEmail(User user, String paidMonthsString, List<Attachment> attachments) throws MessagingException {
        String subject = "";
        String line1 = "";
        String line2 = "";
        String line3 = "";
        switch (user.getLanguage()) {
            case HU:
                subject = "Fizetési igazolás";
                line1 = "Tisztelt [SURNAME] [LASTNAME],";
                line2 = "sikeresen befizette a havi tagdíjat a következő hónap(ok)ra:";
                line3 = "A számlát mellékletként csatoltuk.";
                break;
            case EN:
                subject = "Your payment receipt";
                line1 = "Dear [SURNAME] [LASTNAME],";
                line2 = "you have successfully paid the membership fees for the following month(s):";
                line3 = "We've sent your payment receipt as an attachment.";
                break;
            default:
                throw new MyException("Unsupported language", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        line1 = line1.replace("[SURNAME]", user.getSurname()).replace("[LASTNAME]", user.getLastname());
        String emailBody = paymentTemplate
                .replace("[LINE_1]", line1)
                .replace("[LINE_2]", line2)
                .replace("[LINE_3]", line3)
                .replace("[RECEIPT_MONTHS]", paidMonthsString);
        emailService.sendMessage(user.getEmail(),
                subject,
                emailBody,
                attachments);
    }

}
