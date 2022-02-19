package com.deac.user.model;

import com.deac.user.persistence.entity.User;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class UserDto {

    private String username;

    private String email;

    List<User.Role> roles;

}
