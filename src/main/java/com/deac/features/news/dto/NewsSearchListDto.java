package com.deac.features.news.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class NewsSearchListDto {

    private List<NewsInfoDto> results;

    private Long numberOfResults;

}
