package com.deac.features.payment.controller.paypal;

import com.deac.exception.MyException;
import com.deac.features.payment.dto.CheckoutItemDto;
import com.deac.features.payment.service.paypal.PaypalPaymentService;
import com.deac.response.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
    public ResponseMessage savePayment(@RequestBody String orderId) {
        return new ResponseMessage(paypalPaymentService.savePayment(orderId));
    }

}
