package com.deac.features.payment.dto;

import lombok.*;

import java.time.YearMonth;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ManualPaymentItemDto {

    private YearMonth month;

    private Long amount;

}
