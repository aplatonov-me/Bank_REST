package com.example.bankcards.dto;

import com.example.bankcards.entity.Role;
import com.example.bankcards.util.ValidEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignRoleRequest {
    @NotBlank
    @ValidEnum(enumClass = Role.RoleName.class)
    private String role;

    @NotNull
    private Long userId;
}
