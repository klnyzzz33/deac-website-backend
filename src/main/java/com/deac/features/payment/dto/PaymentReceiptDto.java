package com.deac.features.payment.dto;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReceiptDto {

    private Long amount;

    private String paymentMethodId;

    private String paymentIntentId;

}
