package com.deac.features.news.persistence.entity;

import com.deac.user.persistence.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import javax.persistence.*;
import java.util.Date;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
public class ModifyEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Integer id;

    @DateTimeFormat(pattern = "yyyy.MM.dd")
    @Column(nullable = false)
    private Date modifyDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private User modifyAuthor;

    public ModifyEntry(Date modifyDate, User modifyAuthor) {
        this.modifyDate = modifyDate;
        this.modifyAuthor = modifyAuthor;
    }

}
