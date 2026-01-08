package com.example.logSleuthEnterprise.controller;


import com.example.logSleuthEnterprise.service.kafka.KafkaLogProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
public class KafkaDebugController {

    @Autowired
    private KafkaTemplate<String, String> simpleKafkaTemplate;

    @Autowired(required = false)
    private KafkaLogProducer kafkaLogProducer;

    @GetMapping("/kafka/debug/status")
    public Map<String, Object> kafkaDebugStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now().toString());

        if (kafkaLogProducer != null) {
            response.put("kafkaLogProducer", "AVAILABLE");
            response.put("status", "KAFKA_CONFIGURED");
        } else {
            response.put("kafkaLogProducer", "NOT_AVAILABLE");
            response.put("status", "KAFKA_NOT_CONFIGURED");
        }

        try {
            simpleKafkaTemplate.send("debug-topic", "Test message at " + Instant.now());
            response.put("simpleKafkaTemplate", "WORKING");
        } catch (Exception e) {
            response.put("simpleKafkaTemplate", "ERROR: " + e.getMessage());
        }

        return response;
    }

    @GetMapping("/kafka/debug/send")
    public Map<String, Object> sendDebugLog() {
        Map<String, Object> response = new HashMap<>();

        if (kafkaLogProducer != null) {
            try {
                kafkaLogProducer.sendLog("debug-service", "DEBUG", "Test log via Kafka", "debug-host");
                response.put("status", "SENT");
                response.put("message", "Debug log sent to Kafka");
            } catch (Exception e) {
                response.put("status", "ERROR");
                response.put("message", e.getMessage());
            }
        } else {
            response.put("status", "PRODUCER_NOT_AVAILABLE");
            response.put("message", "KafkaLogProducer is not configured");
        }

        response.put("timestamp", Instant.now().toString());
        return response;
    }

    @GetMapping("/kafka/debug/create-topic")
    public Map<String, Object> createDebugTopic() {
        Map<String, Object> response = new HashMap<>();

        try {
            // Отправляем сообщение - topic создастся автоматически если auto.create.topics.enable=true
            simpleKafkaTemplate.send("raw-logs", "Test message to create topic");
            response.put("status", "TOPIC_CREATED_OR_EXISTS");
            response.put("topic", "raw-logs");
        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
        }

        response.put("timestamp", Instant.now().toString());
        return response;
    }
}
