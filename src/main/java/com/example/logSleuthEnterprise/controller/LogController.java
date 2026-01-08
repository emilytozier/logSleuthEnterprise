package com.example.logSleuthEnterprise.controller;

import com.example.logSleuthEnterprise.dao.LogDAO;
import com.example.logSleuthEnterprise.model.ElasticLogDocument;
import com.example.logSleuthEnterprise.model.KafkaLogMessage;
import com.example.logSleuthEnterprise.service.elastic.ElasticsearchService;
import com.example.logSleuthEnterprise.service.kafka.KafkaLogProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LogController {

    private static final Logger log = LoggerFactory.getLogger(LogController.class);

    private final LogDAO logDao;
    private final KafkaLogProducer kafkaLogProducer;
    private final ElasticsearchService elasticService;

    public LogController(LogDAO logDao,
                         KafkaLogProducer kafkaLogProducer,
                         ElasticsearchService elasticService) {
        this.logDao = logDao;
        this.kafkaLogProducer = kafkaLogProducer;
        this.elasticService = elasticService;
        log.info(" LogController initialized with all dependencies");
    }



    @GetMapping("/")
    public Map<String, Object> home() {
        Map<String, Object> response = new HashMap<>();
        response.put("application", "Log Sleuth Enterprise");
        response.put("version", "1.0.0");
        response.put("status", "operational");
        response.put("timestamp", Instant.now().toString());
        response.put("endpoints", List.of(
                "GET  /api/             - This info page",
                "GET  /api/ping         - Simple health check",
                "GET  /api/status       - Detailed status",
                "GET  /api/health       - Full health check",
                "GET  /api/debug/beans  - Check dependency injection",

                "GET  /api/logs         - Get all logs (limit param)",
                "POST /api/logs         - Create log directly",
                "POST /api/logs/kafka   - Send log via Kafka",
                "DELETE /api/logs       - Clear all logs",

                "GET  /api/elastic/status - Check Elasticsearch",
                "GET  /api/elastic/test   - Test Elasticsearch",

                "GET  /api/kafka/test   - Send test to Kafka",
                "GET  /api/kafka/status - Kafka status"
        ));
        return response;
    }

    @GetMapping("/ping")
    public Map<String, String> ping() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "pong");
        response.put("timestamp", Instant.now().toString());
        return response;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> status = new HashMap<>();
        status.put("application", "Log Sleuth Enterprise");
        status.put("version", "1.0.0");
        status.put("status", "running");
        status.put("timestamp", Instant.now().toString());
        status.put("database", "Cassandra");
        status.put("message_queue", "Kafka");
        status.put("search_engine", "Elasticsearch");
        return status;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();

        Map<String, Object> components = new HashMap<>();

        // Cassandra health
        try {
            List<Map<String, Object>> logs = logDao.getAllLogs(1);
            components.put("cassandra", Map.of(
                    "status", "UP",
                    "details", "Connected successfully"
            ));
        } catch (Exception e) {
            components.put("cassandra", Map.of(
                    "status", "DOWN",
                    "details", e.getMessage()
            ));
        }

        // Kafka health (проверяем только наличие бина)
        components.put("kafka", Map.of(
                "status", kafkaLogProducer != null ? "UP" : "DOWN",
                "details", kafkaLogProducer != null ? "Producer available" : "Producer not available"
        ));

        // Elasticsearch health
        try {
            boolean elasticAvailable = elasticService.isElasticsearchAvailable();
            components.put("elasticsearch", Map.of(
                    "status", elasticAvailable ? "UP" : "DOWN",
                    "details", elasticAvailable ? "Connected" : "Not connected"
            ));
        } catch (Exception e) {
            components.put("elasticsearch", Map.of(
                    "status", "DOWN",
                    "details", e.getMessage()
            ));
        }

        health.put("status", "UP");
        health.put("timestamp", Instant.now().toString());
        health.put("components", components);

        return health;
    }

    @GetMapping("/debug/beans")
    public Map<String, Object> debugBeans() {
        Map<String, Object> debug = new HashMap<>();

        debug.put("logDao", logDao != null ? "INJECTED" : "NULL");
        debug.put("kafkaLogProducer", kafkaLogProducer != null ? "INJECTED" : "NULL");
        debug.put("elasticService", elasticService != null ? "INJECTED" : "NULL");

        if (logDao != null) {
            debug.put("logDaoClass", logDao.getClass().getName());
        }
        if (kafkaLogProducer != null) {
            debug.put("kafkaLogProducerClass", kafkaLogProducer.getClass().getName());
        }
        if (elasticService != null) {
            debug.put("elasticServiceClass", elasticService.getClass().getName());
        }

        debug.put("timestamp", Instant.now().toString());
        debug.put("message", "Check if Spring beans are properly injected");

        return debug;
    }

    // ==================== ЛОГИ (CASSANDRA) ====================

    @PostMapping("/logs")
    public Map<String, Object> createLog(@RequestBody Map<String, Object> logRequest) {
        Map<String, Object> response = new HashMap<>();

        try {
            String service = (String) logRequest.getOrDefault("service", "unknown");
            String level = (String) logRequest.getOrDefault("level", "INFO");
            String message = (String) logRequest.getOrDefault("message", "");
            String host = (String) logRequest.getOrDefault("host", "localhost");

            @SuppressWarnings("unchecked")
            Map<String, String> metadata = (Map<String, String>) logRequest.getOrDefault("metadata", new HashMap<>());

            Map<String, Object> savedLog = logDao.saveLog(service, level, message, host, metadata);

            response.put("status", "created");
            response.put("log", savedLog);
            response.put("message", "Log saved directly to Cassandra");

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            log.error("Failed to create log: {}", e.getMessage(), e);
        }

        response.put("timestamp", Instant.now().toString());
        return response;
    }

    @GetMapping("/logs")
    public Map<String, Object> getLogs(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String level) {

        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> logs;

            if (service != null && !service.isEmpty()) {
                logs = logDao.getLogsByService(service, limit);
                response.put("filter", "service=" + service);
            } else if (level != null && !level.isEmpty()) {
                logs = logDao.getLogsByLevel(level, limit);
                response.put("filter", "level=" + level);
            } else {
                logs = logDao.getAllLogs(limit);
                response.put("filter", "all");
            }

            response.put("status", "success");
            response.put("logs", logs);
            response.put("count", logs.size());
            response.put("limit", limit);

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            log.error("Failed to get logs: {}", e.getMessage());
        }

        response.put("timestamp", Instant.now().toString());
        return response;
    }

    @GetMapping("/logs/stats")
    public Map<String, Object> getLogStats() {
        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, Object> stats = logDao.getStats();
            response.put("status", "success");
            response.put("stats", stats);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        response.put("timestamp", Instant.now().toString());
        return response;
    }

    @DeleteMapping("/logs")
    public Map<String, Object> deleteLogs() {
        Map<String, Object> response = new HashMap<>();

        try {
            logDao.clearAllLogs();
            response.put("status", "deleted");
            response.put("message", "All logs cleared from Cassandra");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        response.put("timestamp", Instant.now().toString());
        return response;
    }

    @GetMapping("/logs/test")
    public Map<String, Object> createTestLogs() {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> testLogs = List.of(
                    Map.of(
                            "service", "auth-service",
                            "level", "INFO",
                            "message", "User login successful",
                            "host", "host-01",
                            "metadata", Map.of("userId", "12345", "ip", "192.168.1.1")
                    ),
                    Map.of(
                            "service", "auth-service",
                            "level", "ERROR",
                            "message", "Invalid credentials",
                            "host", "host-01",
                            "metadata", Map.of("userId", "guest", "reason", "wrong_password")
                    ),
                    Map.of(
                            "service", "payment-service",
                            "level", "INFO",
                            "message", "Payment processed",
                            "host", "host-02",
                            "metadata", Map.of("amount", "100.00", "currency", "USD")
                    )
            );

            List<Map<String, Object>> savedLogs = logDao.saveLogsBatch(testLogs);

            response.put("status", "test_data_created");
            response.put("count", savedLogs.size());
            response.put("message", "Test logs created directly in Cassandra");

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        response.put("timestamp", Instant.now().toString());
        return response;
    }


    @PostMapping("/logs/kafka")
    public Map<String, Object> createLogViaKafka(@RequestBody Map<String, Object> logRequest) {
        Map<String, Object> response = new HashMap<>();

        try {
            String service = (String) logRequest.getOrDefault("service", "unknown");
            String level = (String) logRequest.getOrDefault("level", "INFO");
            String message = (String) logRequest.getOrDefault("message", "");
            String host = (String) logRequest.getOrDefault("host", "localhost");

            @SuppressWarnings("unchecked")
            Map<String, String> metadata = (Map<String, String>) logRequest.getOrDefault("metadata", new HashMap<>());

            KafkaLogMessage kafkaMessage = new KafkaLogMessage(service, level, message, host);
            kafkaMessage.setMetadata(metadata);

            kafkaLogProducer.sendLog(kafkaMessage);

            response.put("status", "sent_to_kafka");
            response.put("message", "Log sent to Kafka for async processing");
            response.put("logId", kafkaMessage.getId());

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            log.error("Failed to send log to Kafka: {}", e.getMessage(), e);
        }

        response.put("timestamp", Instant.now().toString());
        return response;
    }

    @GetMapping("/kafka/test")
    public Map<String, Object> testKafka() {
        Map<String, Object> response = new HashMap<>();

        try {
            // Отправляем несколько тестовых сообщений в Kafka
            kafkaLogProducer.sendLog("kafka-test-service-1", "INFO", "Test message 1 via Kafka", "kafka-host");
            kafkaLogProducer.sendLog("kafka-test-service-2", "WARN", "Test message 2 via Kafka", "kafka-host");
            kafkaLogProducer.sendLog("kafka-test-service-3", "ERROR", "Test message 3 via Kafka", "kafka-host");

            response.put("status", "test_messages_sent");
            response.put("count", 3);
            response.put("message", "3 test messages sent to Kafka");

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        response.put("timestamp", Instant.now().toString());
        return response;
    }

    @GetMapping("/kafka/status")
    public Map<String, Object> kafkaStatus() {
        Map<String, Object> response = new HashMap<>();

        response.put("kafka", "configured");
        response.put("producer", kafkaLogProducer != null ? "ready" : "not_available");
        response.put("topics", List.of("raw-logs"));
        response.put("consumer_group", "log-sleuth-group");

        response.put("timestamp", Instant.now().toString());
        return response;
    }

    @GetMapping("/elastic/status")
    public Map<String, Object> elasticsearchStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean isAvailable = elasticService.isElasticsearchAvailable();

            response.put("status", isAvailable ? "available" : "unavailable");
            response.put("elasticsearch", isAvailable ? "UP" : "DOWN");
            response.put("message", isAvailable ?
                    "Elasticsearch is connected and ready" :
                    "Elasticsearch is not available");

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            log.error("Failed to check Elasticsearch status: {}", e.getMessage());
        }

        response.put("timestamp", Instant.now().toString());
        return response;
    }

    @GetMapping("/elastic/test")
    public Map<String, Object> testElasticsearch() {
        Map<String, Object> response = new HashMap<>();

        try {

            boolean isAvailable = elasticService.isElasticsearchAvailable();

            if (isAvailable) {

                ElasticLogDocument testDoc = new ElasticLogDocument();
                testDoc.setId("test-" + Instant.now().toEpochMilli());
                testDoc.setTimestamp(Instant.now());
                testDoc.setService("test-service");
                testDoc.setLevel("INFO");
                testDoc.setMessage("Test log message for Elasticsearch");
                testDoc.setHost("localhost");
                testDoc.setMetadata(Map.of("test", "true", "source", "controller"));

                elasticService.saveToElasticsearch(testDoc);

                response.put("status", "success");
                response.put("message", "Test document saved to Elasticsearch");
                response.put("documentId", testDoc.getId());
                response.put("action", "indexed");

            } else {
                response.put("status", "unavailable");
                response.put("message", "Elasticsearch is not available, cannot save test document");
            }

            response.put("available", isAvailable);

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            log.error("Failed to test Elasticsearch: {}", e.getMessage(), e);
        }

        response.put("timestamp", Instant.now().toString());
        return response;
    }

    @GetMapping("/elastic/send-test")
    public Map<String, Object> sendTestToElasticsearch() {
        Map<String, Object> response = new HashMap<>();

        try {
            // Создаем тестовый лог и отправляем через Kafka (чтобы проверить полный поток)
            KafkaLogMessage testMessage = new KafkaLogMessage(
                    "elastic-test-service",
                    "DEBUG",
                    "Test log for Elasticsearch integration",
                    "test-host"
            );
            testMessage.setMetadata(Map.of(
                    "test", "true",
                    "purpose", "elasticsearch-integration-test",
                    "timestamp", Instant.now().toString()
            ));

            kafkaLogProducer.sendLog(testMessage);

            response.put("status", "sent_via_kafka");
            response.put("message", "Test log sent to Kafka, will be processed and saved to Elasticsearch");
            response.put("logId", testMessage.getId());
            response.put("note", "Check Kafka consumer logs and Elasticsearch for the result");

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        response.put("timestamp", Instant.now().toString());
        return response;
    }
}
