package com.deac.features.support.dto;

import com.deac.user.service.Language;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TicketCreateDto {

    private String content;

    private String issuerEmail;

    private Language issuerLanguage;

}
