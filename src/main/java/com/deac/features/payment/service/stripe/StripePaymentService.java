package com.deac.features.payment.service.stripe;

import com.deac.features.payment.dto.PaymentConfirmDto;
import com.deac.features.payment.dto.PaymentMethodDto;
import com.deac.features.payment.dto.PaymentStatusDto;

import java.util.List;

public interface StripePaymentService {

    List<PaymentMethodDto> listPaymentMethods();

    PaymentStatusDto makePayment(PaymentConfirmDto paymentConfirmDto);

    PaymentStatusDto makePaymentWithSavedPaymentMethod(PaymentConfirmDto paymentConfirmDto);

    PaymentStatusDto makePaymentAfterAuthentication(String paymentIntentId);

    String setDefaultPaymentMethod(String paymentMethodId);

    String removePaymentMethod(String paymentMethodId);

    String savePayment(String paymentIntentId);

}
