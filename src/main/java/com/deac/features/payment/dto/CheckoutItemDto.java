package com.deac.features.payment.dto;

import lombok.*;

import java.time.YearMonth;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutItemDto {

    private YearMonth monthlyTransactionReceiptMonth;

    private Long amount;

}
