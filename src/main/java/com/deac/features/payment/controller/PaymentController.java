package com.deac.features.payment.controller;

import com.deac.features.payment.dto.*;
import com.deac.features.payment.service.PaymentService;
import com.deac.response.ResponseMessage;
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

    @PostMapping("/api/payment/checkout_info")
    public CheckoutInfoDto getCheckoutInfo() {
        return paymentService.listCheckoutInfo();
    }

    @PostMapping("/api/payment/list_methods")
    public List<PaymentMethodDto> listPaymentMethods() {
        return paymentService.listPaymentMethods();
    }

    @PostMapping("/api/payment/confirm")
    public PaymentStatusDto makePayment(@RequestBody PaymentConfirmDto paymentConfirmDto) {
        return paymentService.makePayment(paymentConfirmDto);
    }

    @PostMapping("/api/payment/saved/confirm")
    public PaymentStatusDto makePaymentWithSavedPaymentMethod(@RequestBody PaymentConfirmDto paymentConfirmDto) {
        return paymentService.makePaymentWithSavedPaymentMethod(paymentConfirmDto);
    }

    @PostMapping("/api/payment/saved/default")
    public ResponseMessage setDefaultPaymentMethod(@RequestBody String paymentMethodId) {
        return new ResponseMessage(paymentService.setDefaultPaymentMethod(paymentMethodId));
    }

    @PostMapping("/api/payment/saved/remove")
    public ResponseMessage removePaymentMethod(@RequestBody String paymentMethodId) {
        return new ResponseMessage(paymentService.removePaymentMethod(paymentMethodId));
    }

    @PostMapping("/api/payment/confirm_after_authenticate")
    public PaymentStatusDto makePaymentAfterAuthentication(@RequestBody String paymentIntentId) {
        return paymentService.makePaymentAfterAuthentication(paymentIntentId);
    }

    @PostMapping("/api/payment/save")
    public ResponseMessage savePayment(@RequestBody PaymentReceiptDto paymentReceiptDto) {
        return new ResponseMessage(paymentService.savePayment(paymentReceiptDto));
    }

}
