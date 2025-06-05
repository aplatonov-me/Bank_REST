package com.example.bankcards.service;

import com.example.bankcards.dto.CardDto;
import com.example.bankcards.dto.CreateCardRequest;
import com.example.bankcards.dto.TransferRequest;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.Role;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.CardException;
import com.example.bankcards.exception.UserNotFoundException;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.util.CardNumberEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CardNumberEncryptor cardNumberEncryptor;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private CardService cardService;

    private User testUser;
    private User adminUser;
    private Card testCard;
    private Card secondCard;

    @BeforeEach
    void setUp() {
        // Setup role
        Role userRole = new Role();
        userRole.setId(1L);
        userRole.setName(Role.RoleName.USER);

        Role adminRole = new Role();
        adminRole.setId(2L);
        adminRole.setName(Role.RoleName.ADMIN);

        // Setup users
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword("password");
        Set<Role> userRoles = new HashSet<>();
        userRoles.add(userRole);
        testUser.setRoles(userRoles);

        adminUser = new User();
        adminUser.setId(2L);
        adminUser.setUsername("admin");
        adminUser.setPassword("adminpass");
        Set<Role> adminRoles = new HashSet<>();
        adminRoles.add(userRole);
        adminRoles.add(adminRole);
        adminUser.setRoles(adminRoles);

        // Setup cards
        testCard = new Card();
        testCard.setId(1L);
        testCard.setCardNumber("encrypted-1234567890123456");
        testCard.setMaskedNumber("**** **** **** 3456");
        testCard.setOwner(testUser);
        testCard.setExpirationDate(LocalDate.now().plusYears(3));
        testCard.setStatus(Card.CardStatus.ACTIVE);
        testCard.setBalance(new BigDecimal("1000.00"));

        secondCard = new Card();
        secondCard.setId(2L);
        secondCard.setCardNumber("encrypted-6543210987654321");
        secondCard.setMaskedNumber("**** **** **** 4321");
        secondCard.setOwner(testUser);
        secondCard.setExpirationDate(LocalDate.now().plusYears(3));
        secondCard.setStatus(Card.CardStatus.ACTIVE);
        secondCard.setBalance(new BigDecimal("500.00"));

        // Set up mock configuration values
        ReflectionTestUtils.setField(cardService, "maxCardsPerUser", 5);
        ReflectionTestUtils.setField(cardService, "defaultExpirationYears", 3);
        ReflectionTestUtils.setField(cardService, "maxTransferAmount", new BigDecimal("100000.00"));

        // Setup security context
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void createCard_ShouldCreateNewCard() {
        // Arrange
        CreateCardRequest request = new CreateCardRequest();
        request.setUserId(1L);
        request.setInitialBalance(new BigDecimal("1000.00"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(cardRepository.countByOwner(testUser)).thenReturn(0L);
        when(cardNumberEncryptor.encrypt(any())).thenReturn("encrypted-card-number");
        when(cardRepository.save(any(Card.class))).thenReturn(testCard);

        // Act
        CardDto result = cardService.createCard(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getMaskedNumber()).isEqualTo("**** **** **** 3456");
        assertThat(result.getOwnerUsername()).isEqualTo("testuser");
        assertThat(result.getBalance()).isEqualTo(new BigDecimal("1000.00"));
        
        verify(userRepository).findById(1L);
        verify(cardRepository).countByOwner(testUser);
        verify(cardNumberEncryptor).encrypt(any());
        verify(cardRepository).save(any(Card.class));
    }

    @Test
    void createCard_UserNotFound_ShouldThrowException() {
        // Arrange
        CreateCardRequest request = new CreateCardRequest();
        request.setUserId(999L);
        request.setInitialBalance(new BigDecimal("1000.00"));

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(UserNotFoundException.class, () -> cardService.createCard(request));
        
        verify(userRepository).findById(999L);
        verifyNoInteractions(cardRepository, cardNumberEncryptor);
    }

    @Test
    void createCard_MaxCardsReached_ShouldThrowException() {
        // Arrange
        CreateCardRequest request = new CreateCardRequest();
        request.setUserId(1L);
        request.setInitialBalance(new BigDecimal("1000.00"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(cardRepository.countByOwner(testUser)).thenReturn(5L);

        // Act & Assert
        assertThrows(CardException.class, () -> cardService.createCard(request));
        
        verify(userRepository).findById(1L);
        verify(cardRepository).countByOwner(testUser);
        verifyNoInteractions(cardNumberEncryptor);
    }

    @Test
    void getCard_AsOwner_ShouldReturnCard() {
        // Arrange
        when(cardRepository.findById(1L)).thenReturn(Optional.of(testCard));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act
        CardDto result = cardService.getCard(1L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getMaskedNumber()).isEqualTo("**** **** **** 3456");
        
        verify(cardRepository).findById(1L);
        verify(securityContext).getAuthentication();
        verify(authentication).getName();
        verify(userRepository).findByUsername("testuser");
    }

    @Test
    void getCard_AsAdmin_ShouldReturnCard() {
        // Arrange
        when(cardRepository.findById(1L)).thenReturn(Optional.of(testCard));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        // Act
        CardDto result = cardService.getCard(1L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        
        verify(cardRepository).findById(1L);
        verify(securityContext).getAuthentication();
        verify(authentication).getName();
        verify(userRepository).findByUsername("admin");
    }

    @Test
    void getCard_Unauthorized_ShouldThrowException() {
        // Arrange
        User otherUser = new User();
        otherUser.setId(3L);
        otherUser.setUsername("otheruser");
        
        when(cardRepository.findById(1L)).thenReturn(Optional.of(testCard));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("otheruser");
        when(userRepository.findByUsername("otheruser")).thenReturn(Optional.of(otherUser));

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> cardService.getCard(1L));
        
        verify(cardRepository).findById(1L);
        verify(securityContext).getAuthentication();
        verify(authentication).getName();
        verify(userRepository).findByUsername("otheruser");
    }

    @Test
    void getUserCards_ShouldReturnUserCards() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Card> cardPage = new PageImpl<>(List.of(testCard, secondCard), pageable, 2);
        
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(cardRepository.findAllByOwner(testUser, pageable)).thenReturn(cardPage);

        // Act
        Page<CardDto> result = cardService.getUserCards(pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(result.getContent().get(1).getId()).isEqualTo(2L);
        
        verify(securityContext).getAuthentication();
        verify(authentication).getName();
        verify(userRepository).findByUsername("testuser");
        verify(cardRepository).findAllByOwner(testUser, pageable);
    }

    @Test
    void transferMoney_ValidTransfer_ShouldSucceed() {
        // Arrange
        TransferRequest request = new TransferRequest();
        request.setSourceCardId(1L);
        request.setDestinationCardId(2L);
        request.setAmount(new BigDecimal("100.00"));
        
        when(cardRepository.findCardForUpdate(1L)).thenReturn(Optional.of(testCard));
        when(cardRepository.findCardForUpdate(2L)).thenReturn(Optional.of(secondCard));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act
        cardService.transferMoney(request);

        // Assert
        assertThat(testCard.getBalance()).isEqualTo(new BigDecimal("900.00"));
        assertThat(secondCard.getBalance()).isEqualTo(new BigDecimal("600.00"));
        
        verify(cardRepository, times(2)).findCardForUpdate(any());
        verify(securityContext).getAuthentication();
        verify(authentication).getName();
        verify(userRepository).findByUsername("testuser");
        verify(cardRepository, times(2)).save(any(Card.class));
    }

    @Test
    void transferMoney_InsufficientFunds_ShouldThrowException() {
        // Arrange
        TransferRequest request = new TransferRequest();
        request.setSourceCardId(1L);
        request.setDestinationCardId(2L);
        request.setAmount(new BigDecimal("2000.00")); // More than available
        
        when(cardRepository.findCardForUpdate(1L)).thenReturn(Optional.of(testCard));
        when(cardRepository.findCardForUpdate(2L)).thenReturn(Optional.of(secondCard));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThrows(CardException.class, () -> cardService.transferMoney(request));
        
        // Verify balances are unchanged
        assertThat(testCard.getBalance()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(secondCard.getBalance()).isEqualTo(new BigDecimal("500.00"));
        
        verify(cardRepository, times(2)).findCardForUpdate(any());
        verify(securityContext).getAuthentication();
        verify(authentication).getName();
        verify(userRepository).findByUsername("testuser");
        verify(cardRepository, never()).save(any(Card.class));
    }
}