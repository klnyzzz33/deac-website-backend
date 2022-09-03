package com.deac.user.dto;

import com.deac.user.persistence.entity.Role;
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

    List<Role> roles;

}
