package com.deac.features.news.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class NewsDto {

    @NotNull(message = "Title not specified")
    private String title;

    @NotNull(message = "Title not specified")
    private String description;

    @NotNull(message = "Content not specified")
    private String content;

}
