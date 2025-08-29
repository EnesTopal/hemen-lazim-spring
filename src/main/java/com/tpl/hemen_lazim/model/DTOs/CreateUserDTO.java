package com.tpl.hemen_lazim.model.DTOs;

import lombok.Data;

@Data
public class CreateUserDTO {

    private String userName;

    private String email;

    private String userPassword;
}
