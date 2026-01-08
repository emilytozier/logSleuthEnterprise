package com.example.logSleuthEnterprise.service.kafka;

import com.example.logSleuthEnterprise.model.KafkaLogMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class KafkaLogProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaLogProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.raw-logs:raw-logs}")
    private String rawLogsTopic;

    public KafkaLogProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        log.info("KafkaLogProducer initialized for topic: {}", rawLogsTopic);
    }

    public void sendLog(KafkaLogMessage logMessage) {
        try {
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("id", logMessage.getId());
            messageData.put("service", logMessage.getService());
            messageData.put("level", logMessage.getLevel());
            messageData.put("message", logMessage.getMessage());
            messageData.put("host", logMessage.getHost());

            if (logMessage.getTimestamp() != null) {
                messageData.put("timestamp", logMessage.getTimestamp().toString());
            } else {
                messageData.put("timestamp", Instant.now().toString());
            }

            if (logMessage.getMetadata() != null) {
                messageData.put("metadata", logMessage.getMetadata());
            } else {
                messageData.put("metadata", new HashMap<>());
            }

            String jsonMessage = objectMapper.writeValueAsString(messageData);

            log.info("Sending to Kafka topic '{}': {}", rawLogsTopic,
                    jsonMessage.substring(0, Math.min(200, jsonMessage.length())) + "...");

            kafkaTemplate.send(rawLogsTopic, logMessage.getId(), jsonMessage);

            log.info("Successfully sent to Kafka: {} - {} [ID: {}]",
                    logMessage.getService(), logMessage.getLevel(), logMessage.getId());

        } catch (Exception e) {
            log.error("Failed to send to Kafka: {}", e.getMessage());
            log.error("Stack trace:", e);
        }
    }

    public void sendLog(String service, String level, String message, String host) {
        KafkaLogMessage logMessage = new KafkaLogMessage(service, level, message, host);
        sendLog(logMessage);
    }

    public void sendTestMessage() {
        KafkaLogMessage testMessage = new KafkaLogMessage(
                "test-service",
                "INFO",
                "Test message from producer",
                "localhost"
        );
        testMessage.getMetadata().put("test", "true");
        testMessage.getMetadata().put("timestamp", Instant.now().toString());

        sendLog(testMessage);
    }
}
