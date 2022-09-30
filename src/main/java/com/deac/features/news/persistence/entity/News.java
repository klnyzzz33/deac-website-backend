package com.deac.features.news.persistence.entity;

import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Integer id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private String indexImageUrl;

    @Column(nullable = false)
    private Integer authorId;

    @DateTimeFormat(pattern = "yyyy.MM.dd")
    @Column(nullable = false)
    private Date createDate;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @ToString.Exclude
    private List<ModifyEntry> modifyEntries;

    public News(String title, String description, String content, String indexImageUrl, Integer authorId, Date createDate) {
        this.title = title;
        this.description = description;
        this.content = content;
        this.indexImageUrl = indexImageUrl;
        this.authorId = authorId;
        this.createDate = createDate;
        this.modifyEntries = new ArrayList<>();
    }

}
