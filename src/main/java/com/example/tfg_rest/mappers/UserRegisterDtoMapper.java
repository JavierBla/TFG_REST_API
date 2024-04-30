package com.example.tfg_rest.mappers;

import com.example.tfg_rest.models.dto.UserRegisterDTO;
import com.example.tfg_rest.models.entity.User;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UserRegisterDtoMapper {

    public static User map(UserRegisterDTO userRegisterDTO) {
        User user = new User();
        user.setUsername(userRegisterDTO.username);
        user.setName(userRegisterDTO.name);
        user.setSurnames(userRegisterDTO.surnames);
        user.setEmail(userRegisterDTO.email);
        user.setBirth_date(userRegisterDTO.birth_date);
        user.setPassword(userRegisterDTO.password);
        return user;
    }
}
