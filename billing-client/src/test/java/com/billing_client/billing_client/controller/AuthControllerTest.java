package com.billing_client.billing_client.controller;

import com.billing_client.billing_client.dto.request.LoginRequestDTO;
import com.billing_client.billing_client.dto.request.RegisterRequestDTO;
import com.billing_client.billing_client.dto.response.AuthResponseDTO;
import com.billing_client.billing_client.exception.EmailAlreadyExistsException;
import com.billing_client.billing_client.model.enums.UserRole;
import com.billing_client.billing_client.security.JwtService;
import com.billing_client.billing_client.security.SecurityConfig;
import com.billing_client.billing_client.service.IAuthService;
import com.billing_client.billing_client.service.impl.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IAuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    // ─────────────────────────────────────────────
    //  LOGIN
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return 200 and token when credentials are valid")
    void shouldReturn200_whenCredentialsAreValid() throws Exception {
        when(authService.login(any())).thenReturn(buildAuthResponse(UserRole.ACCOUNTANT));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLoginRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.type").value("Bearer"));
    }

    @Test
    @DisplayName("Should return 401 when credentials are invalid")
    void shouldReturn401_whenCredentialsAreInvalid() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLoginRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    @DisplayName("Should return 400 when login email is missing")
    void shouldReturn400_whenLoginEmailIsMissing() throws Exception {
        LoginRequestDTO request = buildLoginRequest();
        request.setEmail(null);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        verifyNoInteractions(authService);
    }

    // ─────────────────────────────────────────────
    //  REGISTER
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return 201 when an ADMIN registers a user")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn201_whenAdminRegistersUser() throws Exception {
        when(authService.register(any())).thenReturn(buildAuthResponse(UserRole.ACCOUNTANT));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRegisterRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ACCOUNTANT"));
    }

    @Test
    @DisplayName("Should return 401 when an anonymous user tries to register")
    @WithAnonymousUser
    void shouldReturn401_whenAnonymousUserRegisters() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRegisterRequest())))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("Should return 403 when an ACCOUNTANT tries to register")
    @WithMockUser(roles = "ACCOUNTANT")
    void shouldReturn403_whenAccountantRegisters() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRegisterRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("Should return 409 when email is already registered")
    @WithMockUser(roles = "ADMIN")
    void shouldReturn409_whenEmailAlreadyExists() throws Exception {
        when(authService.register(any()))
                .thenThrow(new EmailAlreadyExistsException("Email already registered: ana@billing.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRegisterRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email Already Exists"));
    }

    // ─────────────────────────────────────────────
    //  BUILDERS
    // ─────────────────────────────────────────────

    private LoginRequestDTO buildLoginRequest() {
        return LoginRequestDTO.builder()
                .email("ana@billing.com")
                .password("secret123")
                .build();
    }

    private RegisterRequestDTO buildRegisterRequest() {
        return RegisterRequestDTO.builder()
                .firstName("Ana")
                .lastName("García")
                .email("ana@billing.com")
                .password("secret123")
                .role(UserRole.ACCOUNTANT)
                .build();
    }

    private AuthResponseDTO buildAuthResponse(UserRole role) {
        return AuthResponseDTO.builder()
                .token("jwt-token")
                .type("Bearer")
                .userId(1L)
                .firstName("Ana")
                .lastName("García")
                .email("ana@billing.com")
                .role(role)
                .build();
    }
}
