package com.deac.features.payment.controller.general;

import com.deac.features.payment.dto.CheckoutInfoDto;
import com.deac.features.payment.service.general.impl.PaymentServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentServiceImpl paymentService;

    @Autowired
    public PaymentController(PaymentServiceImpl paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/payment/checkout_info")
    public CheckoutInfoDto getCheckoutInfo() {
        return paymentService.listCheckoutInfo();
    }

}
