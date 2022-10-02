package com.deac.features.support.dto;

import lombok.*;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TicketDetailInfoDto {

    private Integer ticketId;

    private String title;

    private String content;

    private String issuerName;

    private Date createDate;

    private boolean closed;

    private List<String> attachments;

    private List<TicketCommentDto> comments;

}
