package com.deac.features.support.dto;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TicketCommentCreateDto {

    private Integer ticketId;

    private String content;

}
