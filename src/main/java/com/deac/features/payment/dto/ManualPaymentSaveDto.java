package com.deac.features.payment.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ManualPaymentSaveDto {

    private String username;

    private List<ManualPaymentItemDto> items;

}
