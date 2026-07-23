package com.billing_client.billing_client.service.impl;


import com.billing_client.billing_client.dto.request.LoginRequestDTO;
import com.billing_client.billing_client.dto.request.RegisterRequestDTO;
import com.billing_client.billing_client.dto.response.AuthResponseDTO;
import com.billing_client.billing_client.exception.EmailAlreadyExistsException;
import com.billing_client.billing_client.model.User;
import com.billing_client.billing_client.repository.IUserRepository;
import com.billing_client.billing_client.security.JwtService;

import com.billing_client.billing_client.service.IAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final IUserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    // ─────────────────────────────────────────────
    //  LOGIN
    // ─────────────────────────────────────────────

    @Override
    public AuthResponseDTO login(LoginRequestDTO request) {
        log.info("Login attempt — email: {}", request.getEmail());

        // AuthenticationManager verifies email + password against the DB
        // If credentials are wrong, it throws BadCredentialsException automatically
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Credentials are valid — load the user and generate the token
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found after authentication"));

        String token = jwtService.generateToken(user);

        log.info("Login successful — email: {} | role: {}", user.getEmail(), user.getRole());

        return buildAuthResponse(token, user);
    }

    // ─────────────────────────────────────────────
    //  REGISTER
    // ─────────────────────────────────────────────

    @Override
    public AuthResponseDTO register(RegisterRequestDTO request) {
        log.info("Register attempt — email: {}", request.getEmail());

        // Check if email is already taken
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(
                    "Email already registered: " + request.getEmail()
            );
        }

        // Build and save the new user
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))  // BCrypt hash
                .role(request.getRole())
                .active(true)
                .build();

        userRepository.save(user);

        String token = jwtService.generateToken(user);

        log.info("User registered — email: {} | role: {}", user.getEmail(), user.getRole());

        return buildAuthResponse(token, user);
    }

    // ─────────────────────────────────────────────
    //  HELPER
    // ─────────────────────────────────────────────

    private AuthResponseDTO buildAuthResponse(String token, User user) {
        return AuthResponseDTO.builder()
                .token(token)
                .type("Bearer")
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}