package com.example.bankcards.dto;

import com.example.bankcards.entity.Card;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCardStatusRequest {
    @NotNull(message = "Card ID is required")
    private Long cardId;
    
    @NotNull(message = "Status is required")
    private Card.CardStatus status;
}