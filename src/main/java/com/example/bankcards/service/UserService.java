package com.example.bankcards.service;

import com.example.bankcards.dto.*;
import com.example.bankcards.entity.Role;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.*;
import com.example.bankcards.repository.RoleRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.util.JwtTokenUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       RoleRepository roleRepository,
                       AuthenticationManager authenticationManager,
                       JwtTokenUtil jwtTokenUtil) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Page<ListUsersResponse> getPaginatedUsers(Pageable pageable) {
        Page<User> userPage = userRepository.findAll(pageable);
        return userPage.map(this::convertToDto);
    }

    private ListUsersResponse convertToDto(User user) {
        ListUsersResponse dto = new ListUsersResponse();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        return dto;
    }

    public CreateUserResponse createUser(CreateUserRequest createUserRequest) {
        Optional<User> existingUser = userRepository.findByUsername(createUserRequest.getUsername());
        if (existingUser.isPresent()) {
            throw new UserAlreadyExistsException("User with username '%s' already exists".formatted(createUserRequest.getUsername()));
        }

        User user = new User();
        user.setUsername(createUserRequest.getUsername());
        user.setPassword(passwordEncoder.encode(createUserRequest.getPassword()));

        Role userRole = roleRepository.findByName(Role.RoleName.USER).orElseThrow();
        user.addRole(userRole);

        user = userRepository.save(user);

        CreateUserResponse createUserResponse = new CreateUserResponse();
        createUserResponse.setUsername(user.getUsername());
        createUserResponse.setId(user.getId());
        return createUserResponse;
    }

    public GetUserResponse getUser(Long id) {
        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isEmpty()) {
            throw new UserNotFoundException("User not found");
        }

        User user = userOptional.get();
        GetUserResponse response = new GetUserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setRoles(user.getRoles().stream().map(role -> role.getName().name()).toList());

        return response;
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    public void assignRole(AssignRoleRequest assignRoleRequest) {
        Optional<User> userOptional = userRepository.findById(assignRoleRequest.getUserId());
        if (userOptional.isEmpty()) {
            throw new UserNotFoundException("User with Id %d not found".formatted(assignRoleRequest.getUserId()));
        }

        Optional<Role> roleOptional = roleRepository.findByName(Role.RoleName.valueOf(assignRoleRequest.getRole()));
        if (roleOptional.isEmpty()) {
            throw new RoleNotFoundException("Role '%s' not found".formatted(assignRoleRequest.getRole()));
        }

        Role role = roleOptional.get();
        User user = userOptional.get();

        if (user.getRoles().contains(role)) {
            throw new RoleAlreadyAssignedException("User already has role '%s'".formatted(role.getName()));
        }

        user.addRole(role);
        userRepository.save(user);
    }

    public void removeRole(RemoveRoleRequest removeRoleRequest) {
        Optional<User> userOptional = userRepository.findById(removeRoleRequest.getUserId());
        if (userOptional.isEmpty()) {
            throw new UserNotFoundException("User with Id %d not found".formatted(removeRoleRequest.getUserId()));
        }

        Optional<Role> roleOptional = roleRepository.findByName(Role.RoleName.valueOf(removeRoleRequest.getRole()));
        if (roleOptional.isEmpty()) {
            throw new RoleNotFoundException("Role '%s' not found".formatted(removeRoleRequest.getRole()));
        }

        Role role = roleOptional.get();
        User user = userOptional.get();

        if (!user.getRoles().contains(role)) {
            throw new RoleNotAssignedException("User doesn't have role '%s'".formatted(role.getName()));
        }

        user.removeRole(role);
        userRepository.save(user);
    }
}
