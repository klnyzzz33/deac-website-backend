package com.deac.features.support.dto;

import com.deac.user.persistence.entity.Role;
import lombok.*;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TicketCommentDto {

    private String content;

    private String issuerName;

    private List<Role> issuerRoles;

    private Date createDate;

}
