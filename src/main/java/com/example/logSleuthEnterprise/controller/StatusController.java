package com.example.logSleuthEnterprise.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
public class StatusController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now().toString());

        Map<String, Object> components = new HashMap<>();
        components.put("web", Map.of("status", "UP"));
        components.put("cassandra", testCassandraConnection());

        health.put("components", components);
        return health;
    }

    private Map<String, Object> testCassandraConnection() {
        Map<String, Object> cassandra = new HashMap<>();

        try {
            var session = com.datastax.oss.driver.api.core.CqlSession.builder()
                    .addContactPoint(new java.net.InetSocketAddress("localhost", 9042))
                    .withLocalDatacenter("datacenter1")
                    .build();

            var result = session.execute("SELECT release_version FROM system.local");
            var row = result.one();

            if (row != null) {
                cassandra.put("status", "UP");
                cassandra.put("version", row.getString("release_version"));
                cassandra.put("message", "Connected successfully");
            } else {
                cassandra.put("status", "UNKNOWN");
                cassandra.put("message", "No data returned");
            }

            session.close();

        } catch (Exception e) {
            cassandra.put("status", "DOWN");
            cassandra.put("message", e.getMessage());
            cassandra.put("error", "Connection failed");
        }

        return cassandra;
    }
}
