package com.deac.features.support.persistence.entity;

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
public class TicketComment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Integer id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.EAGER)
    @ToString.Exclude
    private User issuer;

    @DateTimeFormat(pattern = "yyyy.MM.dd")
    @Column(nullable = false)
    private Date createDate = new Date();

    public TicketComment(String content, User issuer) {
        this.content = content;
        this.issuer = issuer;
    }

}
