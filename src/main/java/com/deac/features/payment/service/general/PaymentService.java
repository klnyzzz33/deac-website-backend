package com.deac.features.payment.service.general;

import com.deac.features.payment.dto.CheckoutInfoDto;
import com.deac.features.payment.dto.CheckoutItemDto;
import com.deac.features.payment.dto.ManualPaymentSaveDto;
import com.deac.mail.Attachment;
import com.deac.user.persistence.entity.User;

import java.util.List;
import java.util.Map;

public interface PaymentService {

    String getCurrency();

    @SuppressWarnings(value = "DuplicatedCode")
    String savePaymentManual(ManualPaymentSaveDto payment);

    CheckoutInfoDto listCheckoutInfo();

    void validateOrder(User user, List<CheckoutItemDto> items);

    @SuppressWarnings("DuplicatedCode")
    Attachment generatePaymentReceipt(String paymentId, String paymentMethodId, Long totalAmount, Map<String, String> items, User currentUser);

    void sendPaymentSuccessEmail(User user, List<String> items, List<Attachment> attachments);
}
