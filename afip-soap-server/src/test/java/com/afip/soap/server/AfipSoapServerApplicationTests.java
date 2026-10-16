package com.afip.soap.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

// In-memory H2 instead of the ./data file used when the app runs
@SpringBootTest
@AutoConfigureTestDatabase
class AfipSoapServerApplicationTests {

	@Test
	void contextLoads() {
	}

}
