package com.example.bankcards.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCardRequest {
    @NotNull(message = "User ID is required")
    private Long userId;
    
    @NotNull(message = "Initial balance is required")
    private BigDecimal initialBalance;
}