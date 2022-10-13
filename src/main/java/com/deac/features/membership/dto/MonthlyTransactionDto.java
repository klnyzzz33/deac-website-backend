package com.deac.features.membership.dto;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyTransactionDto {

    private Date yearMonth;

    private String monthlyTransactionReceiptPath;

}
