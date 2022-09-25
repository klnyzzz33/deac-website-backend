package com.deac.features.payment.service;

import com.deac.exception.MyException;
import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.features.membership.persistence.entity.MonthlyTransaction;
import com.deac.features.payment.dto.*;
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
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("DuplicatedCode")
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

    public CheckoutInfoDto listCheckoutInfo() {
        MembershipEntry currentUserMembershipEntry = checkIfMembershipAlreadyPaid();
        boolean isHuf = "huf".equals(currency);
        List<CheckoutItemDto> items;
        if (isHuf) {
            items = currentUserMembershipEntry.getMonthlyTransactions().values()
                    .stream()
                    .filter(monthlyTransaction -> YearMonth.now().minusMonths(3L).isBefore(monthlyTransaction.getMonthlyTransactionReceiptMonth()))
                    .filter(monthlyTransaction -> monthlyTransaction.getMonthlyTransactionReceiptPath() == null)
                    .map(monthlyTransaction -> new CheckoutItemDto(monthlyTransaction.getMonthlyTransactionReceiptMonth(), amount / 100))
                    .sorted(Comparator.comparing(CheckoutItemDto::getMonthlyTransactionReceiptMonth).reversed())
                    .collect(Collectors.toList());
        } else {
            items = currentUserMembershipEntry.getMonthlyTransactions().values()
                    .stream()
                    .filter(monthlyTransaction -> YearMonth.now().minusMonths(3L).isBefore(monthlyTransaction.getMonthlyTransactionReceiptMonth()))
                    .filter(monthlyTransaction -> monthlyTransaction.getMonthlyTransactionReceiptPath() == null)
                    .map(monthlyTransaction -> new CheckoutItemDto(monthlyTransaction.getMonthlyTransactionReceiptMonth(), amount))
                    .sorted(Comparator.comparing(CheckoutItemDto::getMonthlyTransactionReceiptMonth).reversed())
                    .collect(Collectors.toList());
        }
        return new CheckoutInfoDto(items, currency);
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
                        .setLimit(10L)
                        .build();
                PaymentMethodCollection paymentMethodCollection = customer.listPaymentMethods(customerListPaymentMethodsParams);
                paymentMethods = paymentMethodCollection.getData();
            } else {
                throw new MyException("Customer was deleted", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (StripeException ignored) {
        }
        PaymentMethod defaultPaymentMethod = null;
        Optional<PaymentMethod> latestAddedPaymentMethodOptional = paymentMethods
                .stream()
                .max(Comparator.comparing(paymentMethod -> Long.valueOf(paymentMethod.getMetadata().get("lastUsed"))));
        if (latestAddedPaymentMethodOptional.isPresent()) {
            defaultPaymentMethod = latestAddedPaymentMethodOptional.get();
        }
        PaymentMethod finalDefaultPaymentMethod = defaultPaymentMethod;
        return paymentMethods.stream()
                .map(paymentMethod -> {
                    PaymentMethod.Card card = paymentMethod.getCard();
                    boolean isDefault = paymentMethod.equals(finalDefaultPaymentMethod);
                    return new PaymentMethodDto(
                            paymentMethod.getId(),
                            card.getLast4(),
                            card.getExpMonth(),
                            card.getExpYear(),
                            card.getBrand(),
                            isDefault
                    );
                })
                .collect(Collectors.toList());
    }

    public PaymentStatusDto makePayment(PaymentConfirmDto paymentConfirmDto) {
        try {
            MembershipEntry currentUserMembershipEntry = checkIfMembershipAlreadyPaid();
            long totalAmount = 0;
            Map<String, String> metaData = new HashMap<>();
            for (CheckoutItemDto item : paymentConfirmDto.getItems()) {
                totalAmount += item.getAmount();
                metaData.put(item.getMonthlyTransactionReceiptMonth().toString(), item.getAmount().toString());
            }
            if ("huf".equals(currency)) {
                totalAmount *= 100;
            }
            PaymentIntentCreateParams.Builder createParams = PaymentIntentCreateParams.builder()
                    .setAmount(totalAmount)
                    .setCurrency(currency)
                    .putAllMetadata(metaData)
                    .setReceiptEmail(userService.getCurrentUser().getEmail())
                    .setPaymentMethod(paymentConfirmDto.getPaymentMethodId())
                    .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.MANUAL)
                    .setConfirm(true);
            if (paymentConfirmDto.isSaveCard()) {
                String customerId = currentUserMembershipEntry.getCustomerId();
                Customer customer;
                try {
                    customer = Customer.retrieve(customerId);
                    if (customer.getDeleted() == null) {
                        createParams.setCustomer(customer.getId());
                        createParams.setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.ON_SESSION);
                    } else {
                        throw new MyException("Customer was deleted", HttpStatus.INTERNAL_SERVER_ERROR);
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

    public PaymentStatusDto makePaymentWithSavedPaymentMethod(PaymentConfirmDto paymentConfirmDto) {
        try {
            MembershipEntry currentUserMembershipEntry = checkIfMembershipAlreadyPaid();
            long totalAmount = 0;
            Map<String, String> metaData = new HashMap<>();
            for (CheckoutItemDto item : paymentConfirmDto.getItems()) {
                totalAmount += item.getAmount();
                metaData.put(item.getMonthlyTransactionReceiptMonth().toString(), item.getAmount().toString());
            }
            if ("huf".equals(currency)) {
                totalAmount *= 100;
            }
            PaymentMethod savedPaymentMethod = PaymentMethod.retrieve(paymentConfirmDto.getPaymentMethodId());
            savedPaymentMethod.getMetadata().put("lastUsed", Long.valueOf(new Date().getTime()).toString());
            PaymentMethodUpdateParams paymentMethodUpdateParams = PaymentMethodUpdateParams.builder()
                    .setMetadata(savedPaymentMethod.getMetadata())
                    .build();
            savedPaymentMethod.update(paymentMethodUpdateParams);
            PaymentIntentCreateParams.Builder createParams = PaymentIntentCreateParams.builder()
                    .setAmount(totalAmount)
                    .setCurrency(currency)
                    .putAllMetadata(metaData)
                    .setReceiptEmail(userService.getCurrentUser().getEmail())
                    .setPaymentMethod(paymentConfirmDto.getPaymentMethodId())
                    .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.MANUAL)
                    .setConfirm(true);
            String customerId = currentUserMembershipEntry.getCustomerId();
            try {
                Customer customer = Customer.retrieve(customerId);
                if (customer.getDeleted() == null) {
                    createParams.setCustomer(customer.getId());
                } else {
                    throw new MyException("Customer was deleted", HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } catch (StripeException e) {
                throw new MyException(e.getUserMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
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

    private MembershipEntry checkIfMembershipAlreadyPaid() {
        User currentUser = userService.getCurrentUser();
        MembershipEntry currentUserMembershipEntry = currentUser.getMembershipEntry();
        List<MonthlyTransaction> monthlyTransactions = currentUserMembershipEntry.getMonthlyTransactions().values()
                .stream()
                .filter(monthlyTransaction -> YearMonth.now().minusMonths(3L).isBefore(monthlyTransaction.getMonthlyTransactionReceiptMonth()))
                .filter(monthlyTransaction -> monthlyTransaction.getMonthlyTransactionReceiptPath() == null)
                .collect(Collectors.toList());
        if (monthlyTransactions.isEmpty()) {
            throw new MyException("Monthly membership fee already paid", HttpStatus.BAD_REQUEST);
        }
        return currentUserMembershipEntry;
    }

    public String setDefaultPaymentMethod(String paymentMethodId) {
        try {
            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            paymentMethod.getMetadata().put("lastUsed", Long.valueOf(new Date().getTime()).toString());
            PaymentMethodUpdateParams paymentMethodUpdateParams = PaymentMethodUpdateParams.builder()
                    .setMetadata(paymentMethod.getMetadata())
                    .build();
            paymentMethod.update(paymentMethodUpdateParams);
            return "Successfully set default payment method";
        } catch (StripeException e) {
            throw new MyException("Could not set default payment method", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public String removePaymentMethod(String paymentMethodId) {
        try {
            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            paymentMethod.detach();
            return "Successfully removed payment method";
        } catch (StripeException e) {
            throw new MyException("Could not remove payment method", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public String savePayment(PaymentReceiptDto paymentReceiptDto) {
        User currentUser = userService.getCurrentUser();
        MembershipEntry currentUserMembershipEntry = currentUser.getMembershipEntry();
        Map<String, MonthlyTransaction> monthlyTransactions = currentUserMembershipEntry.getMonthlyTransactions();
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM");
            if (!monthlyTransactions.containsKey(YearMonth.now().format(formatter))) {
                monthlyTransactions.put(YearMonth.now().format(formatter), new MonthlyTransaction(YearMonth.now(), null));
            }
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentReceiptDto.getPaymentIntentId());
            Map<String, String> items = paymentIntent.getMetadata();
            String monthlyTransactionReceiptPath = generatePaymentReceipt(paymentReceiptDto, items, currentUser);
            for (Map.Entry<String, String> itemEntry : items.entrySet()) {
                String yearMonth = YearMonth.parse(itemEntry.getKey()).format(formatter);
                monthlyTransactions.get(yearMonth).setMonthlyTransactionReceiptPath(monthlyTransactionReceiptPath);
            }
        } catch (Exception e) {
            throw new MyException("Could not save payment", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        currentUser.setMembershipEntry(currentUserMembershipEntry);
        currentUserMembershipEntry.setHasPaidMembershipFee(true);
        currentUserMembershipEntry.setApproved(true);
        userService.saveUser(currentUser);
        return "Payment successfully saved";
    }

    private String generatePaymentReceipt(PaymentReceiptDto paymentReceiptDto, Map<String, String> items, User currentUser) {
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
                    textWidth = fontNormal.getStringWidth(text) / 1000 * 13;
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
