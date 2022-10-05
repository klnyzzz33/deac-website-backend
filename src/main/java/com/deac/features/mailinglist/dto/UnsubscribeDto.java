package com.deac.features.mailinglist.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class UnsubscribeDto {

    private String email;

    private String token;

}
