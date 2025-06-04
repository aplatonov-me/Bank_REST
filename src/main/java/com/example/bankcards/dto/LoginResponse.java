package com.example.bankcards.dto;

import lombok.Data;

import java.util.List;

@Data
public class LoginResponse {
    private Long id;
    private String username;
    private List<String> roles;
    private String token;
}
