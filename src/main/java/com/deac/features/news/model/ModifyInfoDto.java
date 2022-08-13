package com.deac.features.news.model;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ModifyInfoDto {

    private Date modifyDate;

    private String modifyAuthor;

}
