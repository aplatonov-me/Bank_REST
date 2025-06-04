package com.example.bankcards.controller;

import com.example.bankcards.dto.*;
import com.example.bankcards.entity.Role;
import com.example.bankcards.repository.RoleRepository;
import com.example.bankcards.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AuthControllerTest {

@Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleRepository roleRepository;

    @BeforeEach
    void setUp() {
        if (roleRepository.findByName(Role.RoleName.USER).isEmpty()) {
            Role userRole = new Role();
            userRole.setName(Role.RoleName.USER);
            roleRepository.save(userRole);

            Role adminRole = new Role();
            adminRole.setName(Role.RoleName.ADMIN);
            roleRepository.save(adminRole);
        }

        // Create a test user
        CreateUserRequest createUserRequest = new CreateUserRequest();
        createUserRequest.setUsername("testuser");
        createUserRequest.setPassword("password");
        CreateUserResponse createUserResponse = userService.createUser(createUserRequest);
    }

    @Test
    void testLoginSuccess() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password");

        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(responseContent, LoginResponse.class);

        assertThat(loginResponse).isNotNull();
        assertThat(loginResponse.getUsername()).isEqualTo("testuser");
        assertThat(loginResponse.getToken()).isNotNull();
        assertThat(loginResponse.getId()).isNotNull();
        assertThat(loginResponse.getRoles()).contains("USER");
    }

    @Test
    void testLoginFailure() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("wrongpassword");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }
}