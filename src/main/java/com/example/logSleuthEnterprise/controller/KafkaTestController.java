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
public class KafkaTestController {

    @Autowired
    private KafkaTemplate<String, String> simpleKafkaTemplate;

    @Autowired
    private KafkaLogProducer kafkaLogProducer;

    @GetMapping("/kafka/simple")
    public Map<String, Object> sendSimpleMessage() {
        String message = "Simple test message at " + Instant.now();
        simpleKafkaTemplate.send("test-topic", message);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "simple_message_sent");
        response.put("topic", "test-topic");
        response.put("message", message);
        response.put("timestamp", Instant.now().toString());
        return response;
    }

    @GetMapping("/kafka/batch-test")
    public Map<String, Object> batchTest() {
        int count = 10;

        for (int i = 0; i < count; i++) {
            kafkaLogProducer.sendLog(
                    "batch-test-service",
                    i % 2 == 0 ? "INFO" : "ERROR",
                    "Batch test message #" + i,
                    "batch-host"
            );
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "batch_sent");
        response.put("count", count);
        response.put("message", count + " test messages sent to Kafka");
        response.put("timestamp", Instant.now().toString());
        return response;
    }
}
