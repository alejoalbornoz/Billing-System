package com.billing_client.billing_client.config;

import com.billing_client.billing_client.model.User;
import com.billing_client.billing_client.model.enums.UserRole;
import com.billing_client.billing_client.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminInitializer Tests")
class AdminInitializerTest {

    @Mock
    private IUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminInitializer adminInitializer;

    @BeforeEach
    void setUp() {
        adminInitializer = new AdminInitializer(
                userRepository, passwordEncoder, "admin@billing.com", "admin12345");
    }

    @Test
    @DisplayName("Should create an active ADMIN with encoded password when none exists")
    void shouldCreateAdmin_whenNoAdminExists() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode("admin12345")).thenReturn("hashed-password");

        adminInitializer.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User admin = captor.getValue();
        assertThat(admin.getEmail()).isEqualTo("admin@billing.com");
        assertThat(admin.getPassword()).isEqualTo("hashed-password");
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should do nothing when an ADMIN already exists")
    void shouldSkip_whenAdminAlreadyExists() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(true);

        adminInitializer.run(null);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }
}
