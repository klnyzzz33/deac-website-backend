package com.deac.features.news.service;

import lombok.*;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private List<Integer> results;

    private long numberOfResults;

}
