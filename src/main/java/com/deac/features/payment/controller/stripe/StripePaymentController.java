package com.deac.features.payment.controller.stripe;

import com.deac.features.payment.dto.*;
import com.deac.features.payment.service.stripe.StripePaymentService;
import com.deac.response.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StripePaymentController {

    private final StripePaymentService stripePaymentService;

    @Autowired
    public StripePaymentController(StripePaymentService stripePaymentService) {
        this.stripePaymentService = stripePaymentService;
    }

    @PostMapping("/api/payment/stripe/list_methods")
    public List<PaymentMethodDto> listPaymentMethods() {
        return stripePaymentService.listPaymentMethods();
    }

    @PostMapping("/api/payment/stripe/confirm")
    public PaymentStatusDto makePayment(@RequestBody PaymentConfirmDto paymentConfirmDto) {
        return stripePaymentService.makePayment(paymentConfirmDto);
    }

    @PostMapping("/api/payment/stripe/saved/confirm")
    public PaymentStatusDto makePaymentWithSavedPaymentMethod(@RequestBody PaymentConfirmDto paymentConfirmDto) {
        return stripePaymentService.makePaymentWithSavedPaymentMethod(paymentConfirmDto);
    }

    @PostMapping("/api/payment/stripe/saved/default")
    public ResponseMessage setDefaultPaymentMethod(@RequestBody String paymentMethodId) {
        return new ResponseMessage(stripePaymentService.setDefaultPaymentMethod(paymentMethodId));
    }

    @PostMapping("/api/payment/stripe/saved/remove")
    public ResponseMessage removePaymentMethod(@RequestBody String paymentMethodId) {
        return new ResponseMessage(stripePaymentService.removePaymentMethod(paymentMethodId));
    }

    @PostMapping("/api/payment/stripe/confirm_after_authenticate")
    public PaymentStatusDto makePaymentAfterAuthentication(@RequestBody String paymentIntentId) {
        return stripePaymentService.makePaymentAfterAuthentication(paymentIntentId);
    }

    @PostMapping("/api/payment/stripe/save")
    public ResponseMessage savePayment(@RequestBody String paymentIntentId) {
        return new ResponseMessage(stripePaymentService.savePayment(paymentIntentId));
    }

}
