package com.deac.features.membership.dto;

import lombok.*;

import java.time.YearMonth;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyTransactionDto {

    private YearMonth yearMonth;

    private String monthlyTransactionReceiptPath;

}
