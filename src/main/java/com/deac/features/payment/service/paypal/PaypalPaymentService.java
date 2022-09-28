package com.deac.features.payment.service.paypal;

import com.deac.features.payment.dto.CheckoutItemDto;

import java.util.List;

public interface PaypalPaymentService {

    Object createOrder(List<CheckoutItemDto> items);

    Object confirmOrder(String orderId);

    String savePayment(String orderId);

}
