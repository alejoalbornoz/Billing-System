package com.billing_client.billing_client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class BillingClientApplication {

	public static void main(String[] args) {

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        SpringApplication.run(BillingClientApplication.class, args);
	}

}
