package com.deac.features.news.dto;

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

    private String indexImageUrl;

    private String author;

    private Date createDate;

    private ModifyInfoDto lastModified;

}
