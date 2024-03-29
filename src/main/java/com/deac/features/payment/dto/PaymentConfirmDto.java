package com.deac.features.payment.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmDto {

    private List<CheckoutItemDto> items;

    private String paymentMethodId;

    private boolean saveCard;

}
