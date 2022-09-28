package com.deac.features.payment.service.general;

import com.deac.exception.MyException;
import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.features.payment.dto.CheckoutInfoDto;
import com.deac.features.payment.dto.CheckoutItemDto;
import com.deac.features.payment.persistence.entity.MonthlyTransaction;
import com.deac.user.persistence.entity.User;
import com.deac.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private final UserService userService;

    private final Long amount;

    private final String currency;

    @Autowired
    public PaymentService(UserService userService, Environment environment) {
        this.userService = userService;
        amount = Objects.requireNonNull(environment.getProperty("membership.amount", Long.class));
        currency = Objects.requireNonNull(environment.getProperty("membership.currency", String.class));
    }

    public CheckoutInfoDto listCheckoutInfo() {
        MembershipEntry currentUserMembershipEntry = checkIfMembershipAlreadyPaid();
        boolean isHuf = "huf".equals(currency);
        List<CheckoutItemDto> items;
        if (isHuf) {
            items = currentUserMembershipEntry.getMonthlyTransactions().values()
                    .stream()
                    .filter(monthlyTransaction -> monthlyTransaction.getMonthlyTransactionReceiptPath() == null)
                    .map(monthlyTransaction -> new CheckoutItemDto(monthlyTransaction.getMonthlyTransactionReceiptMonth(), amount / 100))
                    .sorted(Comparator.comparing(CheckoutItemDto::getMonthlyTransactionReceiptMonth).reversed())
                    .collect(Collectors.toList());
        } else {
            items = currentUserMembershipEntry.getMonthlyTransactions().values()
                    .stream()
                    .filter(monthlyTransaction -> monthlyTransaction.getMonthlyTransactionReceiptPath() == null)
                    .map(monthlyTransaction -> new CheckoutItemDto(monthlyTransaction.getMonthlyTransactionReceiptMonth(), amount))
                    .sorted(Comparator.comparing(CheckoutItemDto::getMonthlyTransactionReceiptMonth).reversed())
                    .collect(Collectors.toList());
        }
        return new CheckoutInfoDto(items, currency);
    }

    public MembershipEntry checkIfMembershipAlreadyPaid() {
        User currentUser = userService.getCurrentUser();
        MembershipEntry currentUserMembershipEntry = currentUser.getMembershipEntry();
        List<MonthlyTransaction> monthlyTransactions = currentUserMembershipEntry.getMonthlyTransactions().values()
                .stream()
                .filter(monthlyTransaction -> monthlyTransaction.getMonthlyTransactionReceiptPath() == null)
                .collect(Collectors.toList());
        if (monthlyTransactions.isEmpty()) {
            throw new MyException("Monthly membership fee already paid", HttpStatus.BAD_REQUEST);
        }
        return currentUserMembershipEntry;
    }

}
