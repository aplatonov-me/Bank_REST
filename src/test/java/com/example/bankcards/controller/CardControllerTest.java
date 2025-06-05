package com.example.bankcards.controller;

import com.example.bankcards.dto.*;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.Role;
import com.example.bankcards.entity.User;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.RoleRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.service.CardService;
import com.example.bankcards.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class CardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CardService cardService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CardRepository cardRepository;

    private Long adminId;
    private Long testUserId;
    private Long testCardId;
    private Long secondCardId;

    @BeforeEach
    void setUp() {
        // Clear any existing cards to avoid test interference
        cardRepository.deleteAll();

        // Set up roles if they don't exist
        if (roleRepository.findByName(Role.RoleName.USER).isEmpty()) {
            Role userRole = new Role();
            userRole.setName(Role.RoleName.USER);
            roleRepository.save(userRole);

            Role adminRole = new Role();
            adminRole.setName(Role.RoleName.ADMIN);
            roleRepository.save(adminRole);
        }

        // Create admin user
        CreateUserRequest createAdminRequest = new CreateUserRequest();
        createAdminRequest.setUsername("admin");
        createAdminRequest.setPassword("admin");
        CreateUserResponse adminResponse = userService.createUser(createAdminRequest);
        adminId = adminResponse.getId();

        // Assign admin role
        AssignRoleRequest assignRoleRequest = new AssignRoleRequest();
        assignRoleRequest.setUserId(adminId);
        assignRoleRequest.setRole(Role.RoleName.ADMIN);
        userService.assignRole(assignRoleRequest);

        // Create test user
        CreateUserRequest createTestUserRequest = new CreateUserRequest();
        createTestUserRequest.setUsername("testuser");
        createTestUserRequest.setPassword("password");
        CreateUserResponse testUserResponse = userService.createUser(createTestUserRequest);
        testUserId = testUserResponse.getId();

        // Create cards for test user
        CreateCardRequest createCardRequest = new CreateCardRequest();
        createCardRequest.setUserId(testUserId);
        createCardRequest.setInitialBalance(new BigDecimal("1000.00"));

        // We need to use admin user to create cards
        testCardId = cardService.createCard(createCardRequest).getId();

        CreateCardRequest createSecondCardRequest = new CreateCardRequest();
        createSecondCardRequest.setUserId(testUserId);
        createSecondCardRequest.setInitialBalance(new BigDecimal("500.00"));
        secondCardId = cardService.createCard(createSecondCardRequest).getId();
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ADMIN"})
    void createCard_WithAdminRole_ShouldReturnCreated() throws Exception {
        CreateCardRequest createCardRequest = new CreateCardRequest();
        createCardRequest.setUserId(testUserId);
        createCardRequest.setInitialBalance(new BigDecimal("2000.00"));

        MvcResult result = mockMvc.perform(post("/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createCardRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.maskedNumber").isString())
                .andExpect(jsonPath("$.ownerUsername").value("testuser"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(2000.00))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        CardDto cardDto = objectMapper.readValue(responseContent, CardDto.class);

        // Verify card was actually created in the database
        Card savedCard = cardRepository.findById(cardDto.getId()).orElse(null);
        assertThat(savedCard).isNotNull();
        assertThat(savedCard.getBalance()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(savedCard.getOwner().getUsername()).isEqualTo("testuser");
    }

    @Test
    @WithMockUser(username = "user", authorities = {"USER"})
    void createCard_WithUserRole_ShouldReturnForbidden() throws Exception {
        CreateCardRequest createCardRequest = new CreateCardRequest();
        createCardRequest.setUserId(testUserId);
        createCardRequest.setInitialBalance(new BigDecimal("2000.00"));

        mockMvc.perform(post("/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createCardRequest)))
                .andExpect(status().isForbidden());

        // Verify no new card was created
        List<Card> allCards = cardRepository.findAll();
        assertThat(allCards).hasSize(2); // Only the two cards created in setUp
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"USER"})
    void getCard_AsOwner_ShouldReturnCard() throws Exception {
        MvcResult result = mockMvc.perform(get("/cards/{id}", testCardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testCardId))
                .andExpect(jsonPath("$.maskedNumber").isString())
                .andExpect(jsonPath("$.ownerUsername").value("testuser"))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        CardDto cardDto = objectMapper.readValue(responseContent, CardDto.class);

        assertThat(cardDto.getId()).isEqualTo(testCardId);
        assertThat(cardDto.getOwnerUsername()).isEqualTo("testuser");
        assertThat(cardDto.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @WithMockUser(username = "otheruser", authorities = {"USER"})
    void getCard_AsNonOwner_ShouldReturnForbidden() throws Exception {
        // Create another user first
        CreateUserRequest createUserRequest = new CreateUserRequest();
        createUserRequest.setUsername("otheruser");
        createUserRequest.setPassword("password");
        userService.createUser(createUserRequest);

        mockMvc.perform(get("/cards/{id}", testCardId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ADMIN"})
    void getCard_AsAdmin_ShouldReturnCard() throws Exception {
        mockMvc.perform(get("/cards/{id}", testCardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testCardId))
                .andExpect(jsonPath("$.ownerUsername").value("testuser"));
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"USER"})
    void getUserCards_ShouldReturnUserCards() throws Exception {
        MvcResult result = mockMvc.perform(get("/cards/my")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andReturn();
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ADMIN"})
    void getAllCards_WithAdminRole_ShouldReturnAllCards() throws Exception {
        MvcResult result = mockMvc.perform(get("/cards")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andReturn();
    }

    @Test
    @WithMockUser(username = "user", authorities = {"USER"})
    void getAllCards_WithUserRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/cards"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"USER"})
    void updateCardStatus_ShouldUpdateStatus() throws Exception {
        UpdateCardStatusRequest updateCardStatusRequest = new UpdateCardStatusRequest();
        updateCardStatusRequest.setCardId(testCardId);
        updateCardStatusRequest.setStatus(Card.CardStatus.BLOCKED);

        MvcResult result = mockMvc.perform(put("/cards/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateCardStatusRequest)))
                .andExpect(status().isNoContent())
                .andReturn();

        // Verify the card status was updated in the database
        Card updatedCard = cardRepository.findById(testCardId).orElseThrow();
        assertThat(updatedCard.getStatus()).isEqualTo(Card.CardStatus.BLOCKED);
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"USER"})
    void transferMoney_ShouldTransferSuccessfully() throws Exception {
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setSourceCardId(testCardId);
        transferRequest.setDestinationCardId(secondCardId);
        transferRequest.setAmount(new BigDecimal("300.00"));

        mockMvc.perform(post("/cards/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isNoContent());

        // Verify the balances were updated correctly
        Card sourceCard = cardRepository.findById(testCardId).orElseThrow();
        Card destinationCard = cardRepository.findById(secondCardId).orElseThrow();

        assertThat(sourceCard.getBalance()).isEqualByComparingTo(new BigDecimal("700.00"));
        assertThat(destinationCard.getBalance()).isEqualByComparingTo(new BigDecimal("800.00"));
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"USER"})
    void transferMoney_InsufficientFunds_ShouldReturnBadRequest() throws Exception {
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setSourceCardId(testCardId);
        transferRequest.setDestinationCardId(secondCardId);
        transferRequest.setAmount(new BigDecimal("2000.00")); // More than available

        mockMvc.perform(post("/cards/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isBadRequest());

        // Verify balances remain unchanged
        Card sourceCard = cardRepository.findById(testCardId).orElseThrow();
        Card destinationCard = cardRepository.findById(secondCardId).orElseThrow();

        assertThat(sourceCard.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(destinationCard.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ADMIN"})
    void deleteCard_WithAdminRole_ShouldDeleteCard() throws Exception {
        mockMvc.perform(delete("/cards/{id}", testCardId))
                .andExpect(status().isNoContent());

        // Verify the card was deleted
        assertThat(cardRepository.findById(testCardId)).isEmpty();
    }

    @Test
    @WithMockUser(username = "user", authorities = {"USER"})
    void deleteCard_WithUserRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(delete("/cards/{id}", testCardId))
                .andExpect(status().isForbidden());

        // Verify the card still exists
        assertThat(cardRepository.findById(testCardId)).isPresent();
    }
}