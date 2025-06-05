package com.example.bankcards.dto;

import com.example.bankcards.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RemoveRoleRequest {
    @NotNull
    private Role.RoleName role;

    @NotNull
    private Long userId;
}
