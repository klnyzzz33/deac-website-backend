package com.deac.features.news.persistance.entity;

import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import javax.persistence.*;
import java.util.Date;

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

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private String authorName;

    @DateTimeFormat(pattern = "yyyy.MM.dd")
    @Column(nullable = false)
    private Date createDate;

    public News(String title, String content, String authorName, Date createDate) {
        this.title = title;
        this.content = content;
        this.authorName = authorName;
        this.createDate = createDate;
    }

}
