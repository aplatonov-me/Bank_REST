package com.example.bankcards.dto;

import lombok.Data;

import java.util.List;

@Data
public class GetUserResponse {
    private Long id;
    private String username;

    private List<String> roles;

}
