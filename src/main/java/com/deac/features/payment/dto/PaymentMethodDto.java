package com.deac.features.payment.dto;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodDto {

    private String id;

    private String last4;

    private Long expMonth;

    private Long expYear;

    private String brand;

    private boolean defaultCard;

}
