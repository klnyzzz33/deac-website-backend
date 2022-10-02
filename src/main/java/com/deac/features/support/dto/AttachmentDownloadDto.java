package com.deac.features.support.dto;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDownloadDto {

    private String type;

    private byte[] data;

}
