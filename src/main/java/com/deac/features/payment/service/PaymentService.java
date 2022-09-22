package com.deac.features.payment.service;

import com.deac.exception.MyException;
import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.features.membership.persistence.entity.MonthlyTransaction;
import com.deac.features.payment.dto.PaymentConfirmDto;
import com.deac.features.payment.dto.PaymentMethodDto;
import com.deac.features.payment.dto.PaymentReceiptDto;
import com.deac.features.payment.dto.PaymentStatusDto;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.UserService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.param.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private final UserService userService;

    private final Long amount;

    private final String currency;

    private final String receiptsBaseDirectory;

    public PaymentService(UserService userService, Environment environment) {
        this.userService = userService;
        Stripe.apiKey = Objects.requireNonNull(environment.getProperty("stripe.apikey", String.class));
        amount = Objects.requireNonNull(environment.getProperty("stripe.membership.amount", Long.class));
        currency = Objects.requireNonNull(environment.getProperty("stripe.membership.currency", String.class));
        receiptsBaseDirectory = Objects.requireNonNull(environment.getProperty("file.receipts.rootdir", String.class));
    }

    public List<PaymentMethodDto> listPaymentMethods() {
        String customerId = userService.getCurrentUser().getMembershipEntry().getCustomerId();
        Customer customer;
        List<PaymentMethod> paymentMethods = List.of();
        try {
            customer = Customer.retrieve(customerId);
            if (customer.getDeleted() == null) {
                CustomerListPaymentMethodsParams customerListPaymentMethodsParams = CustomerListPaymentMethodsParams.builder()
                        .setType(CustomerListPaymentMethodsParams.Type.CARD)
                        .build();
                PaymentMethodCollection paymentMethodCollection = customer.listPaymentMethods(customerListPaymentMethodsParams);
                paymentMethods = paymentMethodCollection.getData();
            }
        } catch (StripeException ignored) {
        }
        return paymentMethods.stream()
                .map(paymentMethod -> {
                    PaymentMethod.Card card = paymentMethod.getCard();
                    return new PaymentMethodDto(
                            paymentMethod.getId(),
                            card.getLast4(),
                            card.getExpMonth(),
                            card.getExpYear(),
                            card.getBrand()
                    );
                })
                .collect(Collectors.toList());
    }

    public PaymentStatusDto makePayment(PaymentConfirmDto paymentConfirmDto) {
        try {
            PaymentIntentCreateParams.Builder createParams = PaymentIntentCreateParams.builder()
                    .setAmount(amount)
                    .setCurrency(currency)
                    .setReceiptEmail(userService.getCurrentUser().getEmail())
                    .setPaymentMethod(paymentConfirmDto.getPaymentMethodId())
                    .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.MANUAL)
                    .setConfirm(true);
            if (paymentConfirmDto.isSaveCard()) {
                String customerId = userService.getCurrentUser().getMembershipEntry().getCustomerId();
                Customer customer;
                try {
                    customer = Customer.retrieve(customerId);
                    if (customer.getDeleted() == null) {
                        createParams.setCustomer(customer.getId());
                        createParams.setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.ON_SESSION);
                    }
                } catch (StripeException ignored) {
                }
            }
            PaymentIntent paymentIntent = PaymentIntent.create(createParams.build());
            return evaluatePaymentStatus(paymentIntent);
        } catch (StripeException e) {
            throw new MyException(e.getUserMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public PaymentStatusDto makePaymentAfterAuthentication(String paymentIntentId) {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            paymentIntent = paymentIntent.confirm();
            return evaluatePaymentStatus(paymentIntent);
        } catch (StripeException e) {
            throw new MyException(e.getUserMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private PaymentStatusDto evaluatePaymentStatus(PaymentIntent paymentIntent) {
        PaymentStatusDto paymentStatusDto = new PaymentStatusDto();
        switch (paymentIntent.getStatus()) {
            case "requires_payment_method":
                throw new MyException("Your card was denied, please provide a new payment method", HttpStatus.INTERNAL_SERVER_ERROR);
            case "requires_action":
                paymentStatusDto.setClientSecret(paymentIntent.getClientSecret());
                paymentStatusDto.setRequiresAction(true);
                break;
            case "succeeded":
                paymentStatusDto.setClientSecret(paymentIntent.getClientSecret());
                paymentStatusDto.setRequiresAction(false);
                break;
            default:
                throw new MyException("Unknown error occurred", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return paymentStatusDto;
    }

    public String savePayment(PaymentReceiptDto paymentReceiptDto) {
        User currentUser = userService.getCurrentUser();
        MembershipEntry currentUserMembershipEntry = currentUser.getMembershipEntry();
        currentUserMembershipEntry.setHasPaidMembershipFee(true);
        currentUserMembershipEntry.setApproved(true);
        try {
            currentUserMembershipEntry.getMonthlyTransactions().add(
                    new MonthlyTransaction(YearMonth.now(), generatePaymentReceipt(paymentReceiptDto, currentUser))
            );
        } catch (Exception ignored) {
        }
        currentUser.setMembershipEntry(currentUserMembershipEntry);
        userService.saveUser(currentUser);
        return "Payment successfully saved";
    }

    @SuppressWarnings("DuplicatedCode")
    private String generatePaymentReceipt(PaymentReceiptDto paymentReceiptDto, User currentUser) {
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
                                fontBold.getStringWidth("Összeg:") / 1000 * 13
                        )
                );
                currentLineHeightPosition = currentLineHeightPosition - textHeight - 15;
                contentStream.newLineAtOffset(margin, currentLineHeightPosition);
                contentStream.showText(text);
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(fontNormal, 13);
                text = paymentReceiptDto.getPaymentIntentId();
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
                PaymentMethod paymentMethod = retrievePaymentMethod(paymentReceiptDto.getPaymentMethodId());
                String cardBrand;
                String last4;
                text = "---";
                if (paymentMethod != null) {
                    cardBrand = paymentMethod.getCard().getBrand();
                    last4 = paymentMethod.getCard().getLast4();
                    text = cardBrand.toUpperCase() + " - " + last4;
                }
                contentStream.newLineAtOffset(margin + maxWidth + 20, currentLineHeightPosition);
                contentStream.showText(text);
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(fontBold, 13);
                text = "Összeg:";
                currentLineHeightPosition = currentLineHeightPosition - textHeight - 10;
                contentStream.newLineAtOffset(margin, currentLineHeightPosition);
                contentStream.showText(text);
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(fontNormal, 13);
                if ("huf".equals(currency)) {
                    text = paymentReceiptDto.getAmount() / 100 + ".00 Ft";
                } else {
                    text = paymentReceiptDto.getAmount().toString() + ".00";
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
                e.printStackTrace();
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

    private PaymentMethod retrievePaymentMethod(String paymentMethodId) {
        try {
            return PaymentMethod.retrieve(paymentMethodId);
        } catch (StripeException e) {
            return null;
        }
    }

}
