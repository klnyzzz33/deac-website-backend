package com.deac.features.payment.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutInfoDto {

   private List<CheckoutItemDto> items;

    private String currency;

}
