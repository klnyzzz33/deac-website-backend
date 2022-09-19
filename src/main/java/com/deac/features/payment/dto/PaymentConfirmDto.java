package com.deac.features.payment.dto;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmDto {

    private String paymentMethodId;

    private boolean saveCard;

}
