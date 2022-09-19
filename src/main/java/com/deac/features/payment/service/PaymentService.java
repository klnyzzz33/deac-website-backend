package com.deac.features.payment.service;

import com.deac.exception.MyException;
import com.deac.features.payment.dto.PaymentConfirmDto;
import com.deac.features.payment.dto.PaymentMethodDto;
import com.deac.features.payment.dto.PaymentStatusDto;
import com.deac.user.service.UserService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.param.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private final Long amount;

    private final String currency;

    private final UserService userService;

    public PaymentService(UserService userService, Environment environment) {
        this.userService = userService;
        Stripe.apiKey = Objects.requireNonNull(environment.getProperty("stripe.apikey", String.class));
        amount = Objects.requireNonNull(environment.getProperty("stripe.membership.amount", Long.class));
        currency = Objects.requireNonNull(environment.getProperty("stripe.membership.currency", String.class));
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
                break;
            default:
                throw new MyException("Unknown error occurred", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return paymentStatusDto;
    }

}
