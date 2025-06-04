package com.example.bankcards.controller;

import com.example.bankcards.dto.*;
import com.example.bankcards.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("users")
public class UsersController {
    private final UserService userService;
    public UsersController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("paginated")
    public Page<ListUsersResponse> getPaginatedUsers(Pageable pageable) {
        return userService.getPaginatedUsers(pageable);
    }

    @GetMapping("{id}")
    public ResponseEntity<GetUserResponse> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUser(id));
    }

    @PostMapping("add")
    public ResponseEntity<CreateUserResponse> addUser(@Valid @RequestBody CreateUserRequest createUserRequest) {
        return ResponseEntity.ok(userService.createUser(createUserRequest));
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("assign-role")
    public ResponseEntity<Void> assignRole(@Valid @RequestBody AssignRoleRequest assignRoleRequest) {
        userService.assignRole(assignRoleRequest);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("remove-role")
    public ResponseEntity<Void> assignRole(@Valid @RequestBody RemoveRoleRequest removeRoleRequest) {
        userService.removeRole(removeRoleRequest);
        return ResponseEntity.noContent().build();
    }
}
