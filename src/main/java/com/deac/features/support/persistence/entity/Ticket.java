package com.deac.features.support.persistence.entity;

import com.deac.user.persistence.entity.User;
import com.deac.user.service.Language;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import javax.persistence.*;
import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private User issuer;

    private String issuerEmail;

    @Column(nullable = false)
    private Language issuerLanguage = Language.HU;

    @DateTimeFormat(pattern = "yyyy.MM.dd")
    @Column(nullable = false)
    private Date createDate = new Date();

    @Column(nullable = false)
    private boolean closed = false;

    @ElementCollection(fetch = FetchType.LAZY)
    private List<String> attachmentPaths;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<TicketComment> comments;

    @DateTimeFormat(pattern = "yyyy.MM.dd")
    private Date updateDate = new Date();

    @Column(nullable = false)
    private boolean viewed = false;

    public Ticket(String title, String content, User issuer, String issuerEmail) {
        this.title = title;
        this.content = content;
        this.issuer = issuer;
        this.issuerEmail = issuerEmail;
    }

}
