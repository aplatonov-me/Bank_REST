package com.example.bankcards.service;

import com.example.bankcards.dto.*;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.CardException;
import com.example.bankcards.exception.UserNotFoundException;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.util.CardNumberEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardService {
    
    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final CardNumberEncryptor cardNumberEncryptor;
    
    @Value("${app.card.max-per-user:5}")
    private int maxCardsPerUser;
    
    @Value("${app.card.default-expiration-years:3}")
    private int defaultExpirationYears;
    
    @Value("${app.card.max-transfer-amount:100000.00}")
    private BigDecimal maxTransferAmount;
    
    /**
     * Generate a new card for a user
     */
    @Transactional
    public CardDto createCard(CreateCardRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + request.getUserId()));
        
        // Check if user has reached the maximum allowed cards
        long cardCount = cardRepository.countByOwner(user);
        if (cardCount >= maxCardsPerUser) {
            throw new CardException("User has reached the maximum number of cards: " + maxCardsPerUser);
        }
        
        // Generate a random card number (in a real system, this would follow a specific algorithm)
        String cardNumber = generateCardNumber();
        String maskedNumber = maskCardNumber(cardNumber);
        
        // Create and save the card
        Card card = Card.builder()
                .cardNumber(cardNumberEncryptor.encrypt(cardNumber)) // Encrypt the card number
                .maskedNumber(maskedNumber)
                .owner(user)
                .expirationDate(LocalDate.now().plusYears(defaultExpirationYears))
                .status(Card.CardStatus.ACTIVE)
                .balance(request.getInitialBalance())
                .build();
        
        Card savedCard = cardRepository.save(card);
        
        return mapToDto(savedCard);
    }
    
    /**
     * Get a card by its ID
     */
    public CardDto getCard(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException("Card not found with ID: " + id));
        
        // Check if the current user is the owner or an admin
        validateCardAccess(card);
        
        return mapToDto(card);
    }
    
    /**
     * Get all cards for the current user
     */
    public Page<CardDto> getUserCards(Pageable pageable) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
        
        Page<Card> cards = cardRepository.findAllByOwner(user, pageable);
        return cards.map(this::mapToDto);
    }
    
    /**
     * Get all cards (admin only)
     */
    public Page<CardDto> getAllCards(Pageable pageable) {
        Page<Card> cards = cardRepository.findAll(pageable);
        return cards.map(this::mapToDto);
    }
    
    /**
     * Update a card's status
     */
    @Transactional
    public void updateCardStatus(UpdateCardStatusRequest request) {
        Card card = cardRepository.findById(request.getCardId())
                .orElseThrow(() -> new CardException("Card not found with ID: " + request.getCardId()));
        
        validateCardAccess(card);
        
        card.setStatus(request.getStatus());
        cardRepository.save(card);
    }
    
    /**
     * Transfer money between cards
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void transferMoney(TransferRequest request) {
        if (request.getAmount().compareTo(maxTransferAmount) > 0) {
            throw new CardException("Transfer amount exceeds the maximum allowed: " + maxTransferAmount);
        }
        
        if (request.getSourceCardId().equals(request.getDestinationCardId())) {
            throw new CardException("Source and destination cards cannot be the same");
        }
        
        Card sourceCard = cardRepository.findCardForUpdate(request.getSourceCardId())
                .orElseThrow(() -> new CardException("Source card not found with ID: " + request.getSourceCardId()));
        
        Card destinationCard = cardRepository.findCardForUpdate(request.getDestinationCardId())
                .orElseThrow(() -> new CardException("Destination card not found with ID: " + request.getDestinationCardId()));
        
        // Check if both cards belong to the current user
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
        
        if (!sourceCard.getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You don't have permission to transfer from this card");
        }
        
        if (!destinationCard.getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You don't have permission to transfer to this card");
        }
        
        // Check if cards are active
        if (sourceCard.getStatus() != Card.CardStatus.ACTIVE) {
            throw new CardException("Source card is not active");
        }
        
        if (destinationCard.getStatus() != Card.CardStatus.ACTIVE) {
            throw new CardException("Destination card is not active");
        }
        
        // Check sufficient balance
        if (sourceCard.getBalance().compareTo(request.getAmount()) < 0) {
            throw new CardException("Insufficient funds in the source card");
        }
        
        // Perform the transfer
        sourceCard.setBalance(sourceCard.getBalance().subtract(request.getAmount()));
        destinationCard.setBalance(destinationCard.getBalance().add(request.getAmount()));
        
        cardRepository.save(sourceCard);
        cardRepository.save(destinationCard);
        
        log.info("Transferred {} from card {} to card {}", 
                request.getAmount(), sourceCard.getMaskedNumber(), destinationCard.getMaskedNumber());
    }
    
    /**
     * Delete a card (admin only)
     */
    @Transactional
    public void deleteCard(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException("Card not found with ID: " + id));
        
        cardRepository.delete(card);
        log.info("Deleted card with ID: {}", id);
    }
    
    // Helper methods
    
    private String generateCardNumber() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        
        // Generate a 16-digit card number
        for (int i = 0; i < 16; i++) {
            sb.append(random.nextInt(10));
        }
        
        return sb.toString();
    }
    
    private String maskCardNumber(String cardNumber) {
        // Format: **** **** **** 1234
        return "**** **** **** " + cardNumber.substring(12);
    }
    
    private void validateCardAccess(Card card) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
        
        boolean isAdmin = currentUser.getRoles().stream()
                .anyMatch(role -> role.getName().name().equals("ADMIN"));
        
        if (!isAdmin && !card.getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You don't have permission to access this card");
        }
    }
    
    private CardDto mapToDto(Card card) {
        return CardDto.builder()
                .id(card.getId())
                .maskedNumber(card.getMaskedNumber())
                .ownerUsername(card.getOwner().getUsername())
                .expirationDate(card.getExpirationDate())
                .status(card.getStatus())
                .balance(card.getBalance())
                .build();
    }
}