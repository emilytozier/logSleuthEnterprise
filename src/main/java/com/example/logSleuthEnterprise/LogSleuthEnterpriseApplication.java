package com.example.logSleuthEnterprise;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LogSleuthEnterpriseApplication {

	public static void main(String[] args) {
		SpringApplication.run(LogSleuthEnterpriseApplication.class, args);
		System.out.println("Log Sleuth Enterprise started successfully!");
		System.out.println("Health check: http://localhost:8081/health");
		System.out.println("Home page: http://localhost:8081/");
	}
}