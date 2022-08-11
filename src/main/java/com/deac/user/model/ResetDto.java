package com.deac.user.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class ResetDto {

    @NotNull(message = "Token not specified")
    private String token;

    @NotNull(message = "Password not specified")
    private String password;

}
