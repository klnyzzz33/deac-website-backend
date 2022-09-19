package com.deac.features.payment.controller;

import com.deac.features.payment.dto.PaymentConfirmDto;
import com.deac.features.payment.dto.PaymentMethodDto;
import com.deac.features.payment.dto.PaymentStatusDto;
import com.deac.features.payment.service.PaymentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/payment/list_methods")
    public List<PaymentMethodDto> listPaymentMethods() {
        return paymentService.listPaymentMethods();
    }

    @PostMapping("/api/payment/confirm")
    public PaymentStatusDto makePayment(@RequestBody PaymentConfirmDto paymentConfirmDto) {
        return paymentService.makePayment(paymentConfirmDto);
    }

    @PostMapping("/api/payment/confirm_after_authenticate")
    public PaymentStatusDto makePaymentAfterAuthentication(@RequestBody String paymentIntentId) {
        return paymentService.makePaymentAfterAuthentication(paymentIntentId);
    }

}
