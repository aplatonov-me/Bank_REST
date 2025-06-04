package com.example.bankcards.service;

import com.example.bankcards.dto.LoginRequest;
import com.example.bankcards.dto.LoginResponse;
import com.example.bankcards.entity.User;
import com.example.bankcards.util.JwtTokenUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtTokenUtil jwtTokenUtil) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
    }

    public LoginResponse login(LoginRequest loginRequest) {
        Authentication authenticate = authenticationManager
            .authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(), loginRequest.getPassword()
                )
            );

        UserDetails userDetails = (UserDetails) authenticate.getPrincipal();
        String token = jwtTokenUtil.generateAccessToken(userDetails);

        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setUsername(userDetails.getUsername());
        loginResponse.setToken(token);

        if (userDetails instanceof User user) {
            loginResponse.setId(user.getId());
            loginResponse.setRoles(user.getRoles().stream().map(role -> role.getName().name()).toList());
        }

        return loginResponse;
    }
}
