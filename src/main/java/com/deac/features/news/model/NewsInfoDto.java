package com.deac.features.news.model;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class NewsInfoDto {

    private Integer newsId;

    private String title;

    private String description;

    private String content;

    private String author;

    private Date createDate;

    private ModifyInfoDto lastModified;

}
