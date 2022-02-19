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
public class LoginDto {

    @NotNull(message = "Username not specified")
    private String username;

    @NotNull(message = "Password not specified")
    private String password;

}
