package com.deac.features.payment.service.stripe.impl;

import com.deac.exception.MyException;
import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.features.payment.persistence.entity.MonthlyTransaction;
import com.deac.features.payment.dto.*;
import com.deac.features.payment.service.general.impl.PaymentServiceImpl;
import com.deac.features.payment.service.stripe.StripePaymentService;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.UserService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.param.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("DuplicatedCode")
@Service
public class StripePaymentServiceImpl implements StripePaymentService {

    private final UserService userService;

    private final PaymentServiceImpl paymentService;

    private final String currency;

    @Autowired
    public StripePaymentServiceImpl(UserService userService, Environment environment, PaymentServiceImpl paymentService) {
        this.userService = userService;
        this.paymentService = paymentService;
        Stripe.apiKey = Objects.requireNonNull(environment.getProperty("stripe.apikey", String.class));
        currency = Objects.requireNonNull(environment.getProperty("membership.currency", String.class));
    }

    @Override
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

    @Override
    public PaymentStatusDto makePayment(PaymentConfirmDto paymentConfirmDto) {
        try {
            MembershipEntry currentUserMembershipEntry = paymentService.checkIfMembershipAlreadyPaid();
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

    @Override
    public PaymentStatusDto makePaymentWithSavedPaymentMethod(PaymentConfirmDto paymentConfirmDto) {
        try {
            MembershipEntry currentUserMembershipEntry = paymentService.checkIfMembershipAlreadyPaid();
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

    @Override
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

    @Override
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

    @Override
    public String removePaymentMethod(String paymentMethodId) {
        try {
            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            paymentMethod.detach();
            return "Successfully removed payment method";
        } catch (StripeException e) {
            throw new MyException("Could not remove payment method", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String savePayment(String paymentIntentId) {
        try {
            User currentUser = userService.getCurrentUser();
            MembershipEntry currentUserMembershipEntry = currentUser.getMembershipEntry();
            Map<String, MonthlyTransaction> monthlyTransactions = currentUserMembershipEntry.getMonthlyTransactions();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM");
            if (!monthlyTransactions.containsKey(YearMonth.now().format(formatter))) {
                monthlyTransactions.put(YearMonth.now().format(formatter), new MonthlyTransaction(YearMonth.now(), null));
            }
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            Comparator<String> comparator = Comparator.comparing(YearMonth::parse);
            SortedMap<String, String> items = new TreeMap<>(comparator.reversed());
            items.putAll(paymentIntent.getMetadata());
            String monthlyTransactionReceiptPath = paymentService.generatePaymentReceipt(paymentIntent.getId(), paymentIntent.getPaymentMethod(), paymentIntent.getAmount(), items, currentUser);
            for (Map.Entry<String, String> itemEntry : items.entrySet()) {
                String yearMonth = YearMonth.parse(itemEntry.getKey()).format(formatter);
                monthlyTransactions.get(yearMonth).setMonthlyTransactionReceiptPath(monthlyTransactionReceiptPath);
            }
            currentUser.setMembershipEntry(currentUserMembershipEntry);
            currentUserMembershipEntry.setHasPaidMembershipFee(true);
            currentUserMembershipEntry.setApproved(true);
            userService.saveUser(currentUser);
            paymentService.sendPaymentSuccessEmail(currentUser, new ArrayList<>(items.keySet()));
            return "Payment successfully saved";
        } catch (Exception e) {
            throw new MyException("Could not save payment", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public static PaymentMethod retrievePaymentMethod(String paymentMethodId) {
        try {
            return PaymentMethod.retrieve(paymentMethodId);
        } catch (StripeException e) {
            return null;
        }
    }

}
