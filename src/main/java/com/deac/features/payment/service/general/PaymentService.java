package com.deac.features.payment.service.general;

import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.features.payment.dto.CheckoutInfoDto;
import com.deac.user.persistence.entity.User;

import java.util.List;
import java.util.Map;

public interface PaymentService {

    CheckoutInfoDto listCheckoutInfo();

    MembershipEntry checkIfMembershipAlreadyPaid();

    @SuppressWarnings("DuplicatedCode")
    String generatePaymentReceipt(String paymentId, String paymentMethodId, Long totalAmount, Map<String, String> items, User currentUser);

    void sendPaymentSuccessEmail(User currentUser, List<String> items);

}
