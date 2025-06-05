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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class UsersControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleRepository roleRepository;

    private Long adminId;
    private Long testUserId;

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

        CreateUserRequest createAdminRequest = new CreateUserRequest();
        createAdminRequest.setUsername("admin");
        createAdminRequest.setPassword("admin");
        CreateUserResponse adminResponse = userService.createUser(createAdminRequest);
        adminId = adminResponse.getId();

        AssignRoleRequest assignRoleRequest = new AssignRoleRequest();
        assignRoleRequest.setUserId(adminId);
        assignRoleRequest.setRole(Role.RoleName.ADMIN);
        userService.assignRole(assignRoleRequest);

        CreateUserRequest createTestUserRequest = new CreateUserRequest();
        createTestUserRequest.setUsername("testuser");
        createTestUserRequest.setPassword("password");
        CreateUserResponse testUserResponse = userService.createUser(createTestUserRequest);
        testUserId = testUserResponse.getId();
    }

    @Test
    @WithMockUser(username = "admin", password = "admin", authorities = {"ADMIN"})
    void testAddUser() throws Exception {
        CreateUserRequest createUserRequest = new CreateUserRequest();
        createUserRequest.setUsername("newuser");
        createUserRequest.setPassword("password");

        MvcResult addUserResult = mockMvc.perform(post("/users/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = addUserResult.getResponse().getContentAsString();
        CreateUserResponse createUserResponse = objectMapper.readValue(responseContent, CreateUserResponse.class);

        assertThat(createUserResponse).isNotNull();
        assertThat(createUserResponse.getUsername()).isEqualTo("newuser");
        assertThat(createUserResponse.getId()).isNotNull();

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("newuser");
        loginRequest.setPassword("password");

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        responseContent = loginResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(responseContent, LoginResponse.class);

        assertThat(loginResponse).isNotNull();
        assertThat(loginResponse.getUsername()).isEqualTo("newuser");
        assertThat(loginResponse.getToken()).isNotNull();
        assertThat(loginResponse.getId()).isNotNull();
        assertThat(loginResponse.getRoles()).contains("USER");
    }

    @Test
    @WithMockUser(username = "admin", password = "admin", authorities = {"ADMIN"})
    void testGetUser() throws Exception {
        MvcResult result = mockMvc.perform(get("/users/{id}", testUserId))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        GetUserResponse userResponse = objectMapper.readValue(responseContent, GetUserResponse.class);

        assertThat(userResponse).isNotNull();
        assertThat(userResponse.getId()).isEqualTo(testUserId);
        assertThat(userResponse.getUsername()).isEqualTo("testuser");
        assertThat(userResponse.getRoles()).contains("USER");
    }

    @Test
    @WithMockUser(username = "admin", password = "admin", authorities = {"ADMIN"})
    void testGetPaginatedUsers() throws Exception {
        MvcResult result = mockMvc.perform(get("/users/paginated")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertThat(responseContent).contains("admin");
        assertThat(responseContent).contains("testuser");
    }

    @Test
    @WithMockUser(username = "admin", password = "admin", authorities = {"ADMIN"})
    void testDeleteUser() throws Exception {
        CreateUserRequest createUserRequest = new CreateUserRequest();
        createUserRequest.setUsername("userToDelete");
        createUserRequest.setPassword("password");
        CreateUserResponse createUserResponse = userService.createUser(createUserRequest);
        Long userToDeleteId = createUserResponse.getId();

        mockMvc.perform(delete("/users/{id}", userToDeleteId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/{id}", userToDeleteId))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "admin", password = "admin", authorities = {"ADMIN"})
    void testAssignRole() throws Exception {
        AssignRoleRequest assignRoleRequest = new AssignRoleRequest();
        assignRoleRequest.setUserId(testUserId);
        assignRoleRequest.setRole(Role.RoleName.ADMIN);

        mockMvc.perform(post("/users/assign-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignRoleRequest)))
                .andExpect(status().isNoContent());

        MvcResult result = mockMvc.perform(get("/users/{id}", testUserId))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        GetUserResponse userResponse = objectMapper.readValue(responseContent, GetUserResponse.class);

        assertThat(userResponse.getRoles()).contains("USER", "ADMIN");
    }

    @Test
    @WithMockUser(username = "admin", password = "admin", authorities = {"ADMIN"})
    void testRemoveRole() throws Exception {
        AssignRoleRequest assignRoleRequest = new AssignRoleRequest();
        assignRoleRequest.setUserId(testUserId);
        assignRoleRequest.setRole(Role.RoleName.ADMIN);
        userService.assignRole(assignRoleRequest);

        MvcResult beforeResult = mockMvc.perform(get("/users/{id}", testUserId))
                .andExpect(status().isOk())
                .andReturn();

        String beforeContent = beforeResult.getResponse().getContentAsString();
        GetUserResponse beforeResponse = objectMapper.readValue(beforeContent, GetUserResponse.class);
        assertThat(beforeResponse.getRoles()).contains("USER", "ADMIN");

        RemoveRoleRequest removeRoleRequest = new RemoveRoleRequest();
        removeRoleRequest.setUserId(testUserId);
        removeRoleRequest.setRole(Role.RoleName.ADMIN);

        mockMvc.perform(post("/users/remove-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(removeRoleRequest)))
                .andExpect(status().isNoContent());

        MvcResult afterResult = mockMvc.perform(get("/users/{id}", testUserId))
                .andExpect(status().isOk())
                .andReturn();

        String afterContent = afterResult.getResponse().getContentAsString();
        GetUserResponse afterResponse = objectMapper.readValue(afterContent, GetUserResponse.class);

        assertThat(afterResponse.getRoles()).contains("USER");
        assertThat(afterResponse.getRoles()).doesNotContain("ADMIN");
    }

    @Test
    @WithMockUser(username = "admin", password = "admin", authorities = {"ADMIN"})
    void testAddUserWithInvalidData() throws Exception {
        CreateUserRequest invalidRequest = new CreateUserRequest();
        invalidRequest.setPassword("password");

        mockMvc.perform(post("/users/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUnauthorizedAccess() throws Exception {
        mockMvc.perform(get("/users/paginated"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"USER"})
    void testAccessDeniedForNonAdmin() throws Exception {
        mockMvc.perform(get("/users/paginated"))
                .andExpect(status().isForbidden());
    }
}

