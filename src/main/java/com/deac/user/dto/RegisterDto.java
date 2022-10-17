package com.deac.user.dto;

import com.deac.user.persistence.entity.Role;
import com.deac.user.service.Language;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class RegisterDto {

    @NotNull(message = "Username not specified")
    private String username;

    @NotNull(message = "Email not specified")
    private String email;

    @NotNull(message = "Password not specified")
    private String password;

    @NotNull(message = "Surname not specified")
    private String surname;

    @NotNull(message = "Lastname not specified")
    private String lastname;

    @NotNull(message = "Roles not specified")
    List<Role> roles;

    @NotNull(message = "Language not specified")
    Language language;

}
