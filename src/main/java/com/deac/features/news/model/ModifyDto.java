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
public class ModifyDto {

    @NotNull(message = "Id not specified")
    private Integer newsId;

    @NotNull(message = "Title not specified")
    private String title;

    @NotNull(message = "Description not specified")
    private String description;

    @NotNull(message = "Content not specified")
    private String content;

}
