package com.deac.features.membership.persistence.entity;

import lombok.*;

import javax.persistence.*;
import java.time.YearMonth;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Integer id;

    @Column(nullable = false, columnDefinition = "date")
    @Convert(converter = YearMonthDateAttributeConverter.class)
    private YearMonth monthlyTransactionReceiptMonth;

    private String monthlyTransactionReceiptPath;

    public MonthlyTransaction(YearMonth monthlyTransactionReceiptMonth, String monthlyTransactionReceiptPath) {
        this.monthlyTransactionReceiptMonth = monthlyTransactionReceiptMonth;
        this.monthlyTransactionReceiptPath = monthlyTransactionReceiptPath;
    }

}
