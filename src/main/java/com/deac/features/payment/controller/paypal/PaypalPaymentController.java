package com.deac.features.payment.controller.paypal;

import com.deac.features.payment.dto.CheckoutItemDto;
import com.deac.features.payment.service.paypal.PaypalPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PaypalPaymentController {

    private final PaypalPaymentService paypalPaymentService;

    @Autowired
    public PaypalPaymentController(PaypalPaymentService paypalPaymentService) {
        this.paypalPaymentService = paypalPaymentService;
    }

    @PostMapping("/api/payment/paypal/order")
    public Object createOrder(@RequestBody List<CheckoutItemDto> items) {
        return paypalPaymentService.createOrder(items);
    }

    @PostMapping("/api/payment/paypal/confirm")
    public Object confirmOrder(@RequestBody String orderId) {
        return paypalPaymentService.confirmOrder(orderId);
    }

    @PostMapping("/api/payment/paypal/save")
    public Object savePayment() {
        return paypalPaymentService.savePayment();
    }

}
