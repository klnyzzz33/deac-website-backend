package com.deac.features.payment.service.general;

import com.deac.exception.MyException;
import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.features.payment.dto.CheckoutInfoDto;
import com.deac.features.payment.dto.CheckoutItemDto;
import com.deac.features.payment.persistence.entity.MonthlyTransaction;
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

import static com.deac.features.payment.service.stripe.StripePaymentService.retrievePaymentMethod;

@Service
public class PaymentService {

    private final UserService userService;

    private final Long amount;

    private final String currency;

    private final String receiptsBaseDirectory;

    @Autowired
    public PaymentService(UserService userService, Environment environment) {
        this.userService = userService;
        amount = Objects.requireNonNull(environment.getProperty("membership.amount", Long.class));
        currency = Objects.requireNonNull(environment.getProperty("membership.currency", String.class));
        receiptsBaseDirectory = Objects.requireNonNull(environment.getProperty("file.receipts.rootdir", String.class));
    }

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

    @SuppressWarnings("DuplicatedCode")
    public String generatePaymentReceipt(String paymentId, String paymentMethodId, Long totalAmount, Map<String, String> items, User currentUser) {
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            float margin = 50;
            PDFont fontNormal = PDType0Font.load(pdf, new ClassPathResource("fonts/Montserrat-Regular.ttf").getFile());
            PDFont fontBold = PDType0Font.load(pdf, new ClassPathResource("fonts/Montserrat-Bold.ttf").getFile());
            String receiptId = UUID.randomUUID().toString();
            try (PDPageContentStream contentStream = new PDPageContentStream(pdf, page)) {
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
                text = currentUser.getSurname() + " " + currentUser.getLastname();
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
                text = "USER-" + currentUser.getId();
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
                text = currentUser.getEmail();
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
                if (!"PayPal".equals(paymentMethodId)) {
                    PaymentMethod paymentMethod = retrievePaymentMethod(paymentMethodId);
                    String cardBrand;
                    String last4;
                    text = "---";
                    if (paymentMethod != null) {
                        cardBrand = paymentMethod.getCard().getBrand();
                        last4 = paymentMethod.getCard().getLast4();
                        text = cardBrand.toUpperCase() + " - " + last4;
                    }
                } else {
                    text = paymentMethodId;
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
            } catch (IOException e) {
                throw new MyException("Could not generate receipt", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            String baseDir = receiptsBaseDirectory + "user_" + currentUser.getId() + "/";
            String filePath = "receipt_" + receiptId + ".pdf";
            Files.createDirectories(Path.of(baseDir));
            String targetPath = baseDir + filePath;
            pdf.save(targetPath);
            return filePath;
        } catch (IOException e) {
            throw new MyException("Could not generate receipt", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
