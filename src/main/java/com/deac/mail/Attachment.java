package com.deac.mail;

import lombok.*;

@Getter
@Setter
@ToString
@AllArgsConstructor
public class Attachment {

    private String name;

    private byte[] content;

}
