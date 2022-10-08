package com.deac.features.news.service;

import lombok.*;
import org.apache.lucene.document.Document;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class LuceneSearchResult {

    private List<Document> results;

    private Long numberOfResults;

}
