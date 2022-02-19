package com.deac.user.model;

import com.deac.user.persistence.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class RegisterDto {

    @NotNull(message = "Username not specified")
    private String username;

    @NotNull(message = "Email not specified")
    private String email;

    @NotNull(message = "Password not specified")
    private String password;

    @NotNull(message = "Roles not specified")
    List<User.Role> roles;

}
