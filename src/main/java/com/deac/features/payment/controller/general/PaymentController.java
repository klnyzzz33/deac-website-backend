package com.deac.features.payment.controller.general;

import com.deac.features.payment.dto.CheckoutInfoDto;
import com.deac.features.payment.dto.ManualPaymentSaveDto;
import com.deac.features.payment.service.general.impl.PaymentServiceImpl;
import com.deac.response.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentServiceImpl paymentService;

    @Autowired
    public PaymentController(PaymentServiceImpl paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/admin/payment/currency")
    public ResponseMessage getCurrency() {
        return new ResponseMessage(paymentService.getCurrency());
    }

    @PostMapping("/api/admin/payment/save_manual")
    public ResponseMessage savePaymentManual(@RequestBody ManualPaymentSaveDto payment) {
        return new ResponseMessage(paymentService.savePaymentManual(payment));
    }

    @PostMapping("/api/payment/checkout_info")
    public CheckoutInfoDto getCheckoutInfo() {
        return paymentService.listCheckoutInfo();
    }

}
