package com.billing_client.billing_client;

import com.billing_client.billing_client.model.enums.UserRole;
import com.billing_client.billing_client.repository.IUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the whole application against a real PostgreSQL run by Testcontainers.
 * Skipped (not failed) when Docker isn't available.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class BillingClientApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

	@Autowired
	private IUserRepository userRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void shouldCreateInitialAdmin_onStartup() {
		assertThat(userRepository.findByEmail("admin@billing.com"))
				.hasValueSatisfying(user -> assertThat(user.getRole()).isEqualTo(UserRole.ADMIN));
	}

}
