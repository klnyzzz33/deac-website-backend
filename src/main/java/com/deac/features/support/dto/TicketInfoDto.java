package com.deac.features.support.dto;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TicketInfoDto {

    private Integer ticketId;

    private String title;

    private String content;

    private String issuerName;

    private Date createDate;

    private boolean closed;

    private boolean viewed;

    private Long unreadComments;

}
