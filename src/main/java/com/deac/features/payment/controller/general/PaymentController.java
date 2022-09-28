package com.deac.features.payment.controller.general;

import com.deac.features.payment.dto.CheckoutInfoDto;
import com.deac.features.payment.service.general.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    @Autowired
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/payment/checkout_info")
    public CheckoutInfoDto getCheckoutInfo() {
        return paymentService.listCheckoutInfo();
    }

}
