
package com.example.bankcards.service;

import com.example.bankcards.dto.*;
import com.example.bankcards.entity.Role;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.*;
import com.example.bankcards.repository.RoleRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.util.JwtTokenUtil;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenUtil jwtTokenUtil;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private Role userRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword("encodedPassword");

        userRole = new Role();
        userRole.setId(1L);
        userRole.setName(Role.RoleName.USER);

        adminRole = new Role();
        adminRole.setId(2L);
        adminRole.setName(Role.RoleName.ADMIN);

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        testUser.setRoles(roles);
    }

    @Test
    void getPaginatedUsers_ShouldReturnPageOfUsers() {
        Pageable pageable = PageRequest.of(0, 10);
        List<User> users = List.of(testUser);
        Page<User> userPage = new PageImpl<>(users, pageable, users.size());
        
        when(userRepository.findAll(pageable)).thenReturn(userPage);

        Page<ListUsersResponse> result = userService.getPaginatedUsers(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(testUser.getId());
        assertThat(result.getContent().get(0).getUsername()).isEqualTo(testUser.getUsername());
        
        verify(userRepository).findAll(pageable);
    }

    @Test
    void createUser_WithNewUsername_ShouldCreateUser() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("newuser");
        request.setPassword("password");

        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
        when(roleRepository.findByName(Role.RoleName.USER)).thenReturn(Optional.of(userRole));
        
        User savedUser = new User();
        savedUser.setId(2L);
        savedUser.setUsername("newuser");
        savedUser.setPassword("encodedPassword");
        savedUser.addRole(userRole);
        
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        CreateUserResponse response = userService.createUser(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(2L);
        assertThat(response.getUsername()).isEqualTo("newuser");
        
        verify(userRepository).findByUsername("newuser");
        verify(passwordEncoder).encode("password");
        verify(roleRepository).findByName(Role.RoleName.USER);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_WithExistingUsername_ShouldThrowException() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("testuser");
        request.setPassword("password");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        UserAlreadyExistsException exception = assertThrows(
            UserAlreadyExistsException.class, 
            () -> userService.createUser(request)
        );
        
        assertThat(exception.getMessage()).contains("testuser");
        
        verify(userRepository).findByUsername("testuser");
        verifyNoMoreInteractions(passwordEncoder, roleRepository, userRepository);
    }

    @Test
    void getUser_WithExistingId_ShouldReturnUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        GetUserResponse response = userService.getUser(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.getRoles()).contains("USER");
        
        verify(userRepository).findById(1L);
    }

    @Test
    void getUser_WithNonExistingId_ShouldThrowException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(
            UserNotFoundException.class, 
            () -> userService.getUser(999L)
        );
        
        assertThat(exception.getMessage()).contains("User not found");
        
        verify(userRepository).findById(999L);
    }

    @Test
    void deleteUser_ShouldCallRepositoryDelete() {
        doNothing().when(userRepository).deleteById(1L);

        userService.deleteUser(1L);

        verify(userRepository).deleteById(1L);
    }

    @Test
    void assignRole_ValidRequest_ShouldAssignRole() {
        AssignRoleRequest request = new AssignRoleRequest();
        request.setUserId(1L);
        request.setRole(Role.RoleName.ADMIN);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByName(Role.RoleName.ADMIN)).thenReturn(Optional.of(adminRole));

        userService.assignRole(request);

        assertThat(testUser.getRoles()).contains(userRole, adminRole);
        
        verify(userRepository).findById(1L);
        verify(roleRepository).findByName(Role.RoleName.ADMIN);
        verify(userRepository).save(testUser);
    }

    @Test
    void assignRole_UserNotFound_ShouldThrowException() {
        AssignRoleRequest request = new AssignRoleRequest();
        request.setUserId(999L);
        request.setRole(Role.RoleName.ADMIN);

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(
            UserNotFoundException.class, 
            () -> userService.assignRole(request)
        );
        
        assertThat(exception.getMessage()).contains("999");
        
        verify(userRepository).findById(999L);
        verifyNoInteractions(roleRepository);
    }

    @Test
    void assignRole_RoleAlreadyAssigned_ShouldThrowException() {
        AssignRoleRequest request = new AssignRoleRequest();
        request.setUserId(1L);
        request.setRole(Role.RoleName.ADMIN);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByName(Role.RoleName.ADMIN)).thenReturn(Optional.of(userRole));

        RoleAlreadyAssignedException exception = assertThrows(
            RoleAlreadyAssignedException.class,
            () -> userService.assignRole(request)
        );
        
        assertThat(exception.getMessage()).contains("USER");
        
        verify(userRepository).findById(1L);
        verify(roleRepository).findByName(Role.RoleName.ADMIN);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void removeRole_ValidRequest_ShouldRemoveRole() {
        testUser.addRole(adminRole);
        
        RemoveRoleRequest request = new RemoveRoleRequest();
        request.setUserId(1L);
        request.setRole(Role.RoleName.ADMIN);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByName(Role.RoleName.ADMIN)).thenReturn(Optional.of(adminRole));

        userService.removeRole(request);

        assertThat(testUser.getRoles()).contains(userRole);
        assertThat(testUser.getRoles()).doesNotContain(adminRole);
        
        verify(userRepository).findById(1L);
        verify(roleRepository).findByName(Role.RoleName.ADMIN);
        verify(userRepository).save(testUser);
    }

    @Test
    void removeRole_UserNotFound_ShouldThrowException() {
        RemoveRoleRequest request = new RemoveRoleRequest();
        request.setUserId(999L);
        request.setRole(Role.RoleName.ADMIN);

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(
            UserNotFoundException.class, 
            () -> userService.removeRole(request)
        );
        
        assertThat(exception.getMessage()).contains("999");
        
        verify(userRepository).findById(999L);
        verifyNoInteractions(roleRepository);
    }

    @Test
    void removeRole_RoleNotAssigned_ShouldThrowException() {
        RemoveRoleRequest request = new RemoveRoleRequest();
        request.setUserId(1L);
        request.setRole(Role.RoleName.ADMIN);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByName(Role.RoleName.ADMIN)).thenReturn(Optional.of(adminRole));

        RoleNotAssignedException exception = assertThrows(
            RoleNotAssignedException.class, 
            () -> userService.removeRole(request)
        );
        
        assertThat(exception.getMessage()).contains("ADMIN");
        
        verify(userRepository).findById(1L);
        verify(roleRepository).findByName(Role.RoleName.ADMIN);
        verifyNoMoreInteractions(userRepository);
    }
}