package com.learn.orderservice.dto;

import com.learn.orderservice.entity.AppUser;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserResponse {

    private Long id;
    private String email;
    private String name;

    public static UserResponse from(AppUser user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setName(user.getName());
        return response;
    }
}
