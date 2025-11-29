package com.example.logSleuthEnterprise.controller;



import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return """
               ✅ Log Sleuth Enterprise - Status: RUNNING
               
               Components:
               - Web Server: ✅ Active
               - Health Checks: ✅ Enabled
               - Cassandra: 🔄 Disabled (will add later)
               - Kafka: 🔄 Disabled (will add later)
               - Elasticsearch: 🔄 Disabled (will add later)
               
               Next steps: We'll add each component one by one!
               """;
    }

    @GetMapping("/")
    public String home() {
        return """
               🚀 Welcome to Log Sleuth Enterprise!
               
               Available endpoints:
               - /health - Application status
               - /actuator/health - Spring Boot health
               
               Technology stack (to be added):
               - Spring Boot 3.1.5
               - Cassandra
               - Kafka
               - Elasticsearch
               - RxJava
               - Prometheus & Grafana
               
               Project is successfully running! 🎉
               """;
    }
}