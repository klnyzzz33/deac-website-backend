package com.deac.features.support.dto;

import lombok.*;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TicketCommentDto {

    private Integer commentId;

    private String title;

    private String content;

    private String issuerName;

    private Date createDate;

    private List<String> attachments;

}
