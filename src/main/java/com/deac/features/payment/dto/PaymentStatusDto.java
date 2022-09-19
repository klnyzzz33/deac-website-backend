package com.deac.features.payment.dto;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusDto {

    private String clientSecret;

    private boolean requiresAction;

}
