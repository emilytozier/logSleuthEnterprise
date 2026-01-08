package com.example.logSleuthEnterprise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LogSleuthEnterpriseApplication {

	public static void main(String[] args) {
		SpringApplication.run(LogSleuthEnterpriseApplication.class, args);
		System.out.println("\n" + "=".repeat(50));
		System.out.println("Log Sleuth Enterprise - SIMPLE VERSION");
		System.out.println("=".repeat(50));
		System.out.println("Application is starting...");
		System.out.println("Check: http://localhost:8081/");
		System.out.println("=".repeat(50) + "\n");
	}
}
