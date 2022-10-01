package com.deac.features.support.dto;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TicketCreateDto {

    private String content;

    private String issuerEmail;

}
