package com.deac.features.news.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class NewsSearchBarDto {

    private List<NewsSearchBarItemDto> results;

    private Long numberOfResults;

}
