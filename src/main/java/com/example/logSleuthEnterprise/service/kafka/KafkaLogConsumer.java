package com.example.logSleuthEnterprise.service.kafka;

import com.example.logSleuthEnterprise.dao.LogDAO;
import com.example.logSleuthEnterprise.model.ElasticLogDocument;
import com.example.logSleuthEnterprise.model.KafkaLogMessage;
import com.example.logSleuthEnterprise.service.elastic.ElasticsearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class KafkaLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaLogConsumer.class);
    private final ObjectMapper objectMapper;
    private final LogDAO logDao;
    private final ElasticsearchService elasticService;

    public KafkaLogConsumer(LogDAO logDao, ElasticsearchService elasticService) {
        this.logDao = logDao;
        this.elasticService = elasticService;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        log.info("KafkaLogConsumer initialized with manual JSON processing");
    }

    @KafkaListener(topics = "${app.kafka.topics.raw-logs}")
    public void consumeRawLog(String jsonMessage) {
        log.info("KAFKA CONSUMER ACTIVATED!");
        log.info("Received raw JSON: {}", jsonMessage);

        try {
            KafkaLogMessage logMessage = parseKafkaMessage(jsonMessage);

            if (logMessage == null) {
                log.error("Failed to parse Kafka message");
                return;
            }

            log.info("SUCCESSFULLY PARSED!");
            log.info("   ID: {}", logMessage.getId());
            log.info("   Service: {}", logMessage.getService());
            log.info("   Level: {}", logMessage.getLevel());
            log.info("   Timestamp: {}", logMessage.getTimestamp());
            log.info("   Host: {}", logMessage.getHost());
            log.info("   Message: {}", logMessage.getMessage());

            // Добавляем метаданные обработки
            if (logMessage.getMetadata() == null) {
                logMessage.setMetadata(new HashMap<>());
            }
            logMessage.getMetadata().put("kafka_processed", "true");
            logMessage.getMetadata().put("processed_at", Instant.now().toString());
            logMessage.getMetadata().put("consumer_version", "2.0");

            log.info("Metadata: {} items", logMessage.getMetadata().size());

            // 1. Сохраняем в Cassandra
            log.info("Saving to Cassandra...");
            Map<String, Object> savedLog = logDao.saveLog(
                    logMessage.getService(),
                    logMessage.getLevel(),
                    logMessage.getMessage(),
                    logMessage.getHost(),
                    logMessage.getMetadata()
            );

            if (savedLog.containsKey("error")) {
                log.error("Failed to save to Cassandra: {}", savedLog.get("error"));
            } else {
                log.info("Saved to Cassandra: {}", logMessage.getId());
            }

            // 2. Сохраняем в Elasticsearch
            if (elasticService.isElasticsearchAvailable()) {
                log.info(" Saving to Elasticsearch...");
                try {
                    ElasticLogDocument elasticDoc = createElasticDocument(logMessage);
                    elasticService.saveToElasticsearch(elasticDoc);
                    log.info("Saved to Elasticsearch: {}", logMessage.getId());
                } catch (Exception e) {
                    log.error("Failed to save to Elasticsearch: {}", e.getMessage());
                }
            } else {
                log.warn("Elasticsearch not available, skipping");
            }

            log.info("PROCESSING COMPLETED SUCCESSFULLY!");
            log.info("Total processing time: {}", System.currentTimeMillis());

        } catch (Exception e) {
            log.error("FAILED to process Kafka message!");
            log.error("Error type: {}", e.getClass().getName());
            log.error("Error message: {}", e.getMessage());
            log.error("Problematic JSON: {}", jsonMessage);
            log.error("Stack trace:", e);

            saveFailedMessage(jsonMessage, e.getMessage());
        }

        log.info("" + "=".repeat(60));
    }

    private KafkaLogMessage parseKafkaMessage(String jsonMessage) throws Exception {
        try {
            return objectMapper.readValue(jsonMessage, KafkaLogMessage.class);
        } catch (Exception e) {
            log.warn("First parsing attempt failed, trying manual parsing: {}", e.getMessage());

            var rootNode = objectMapper.readTree(jsonMessage);
            KafkaLogMessage message = new KafkaLogMessage();

            if (rootNode.has("id")) message.setId(rootNode.get("id").asText());
            if (rootNode.has("service")) message.setService(rootNode.get("service").asText());
            if (rootNode.has("level")) message.setLevel(rootNode.get("level").asText());
            if (rootNode.has("message")) message.setMessage(rootNode.get("message").asText());
            if (rootNode.has("host")) message.setHost(rootNode.get("host").asText());

            if (rootNode.has("timestamp")) {
                var timestampNode = rootNode.get("timestamp");
                if (timestampNode.isTextual()) {
                    // Если строка в ISO формате
                    message.setTimestamp(Instant.parse(timestampNode.asText()));
                } else if (timestampNode.isNumber()) {
                    // Если число (секунды с миллисекундами)
                    double seconds = timestampNode.asDouble();
                    long sec = (long) seconds;
                    long nanos = (long) ((seconds - sec) * 1_000_000_000);
                    message.setTimestamp(Instant.ofEpochSecond(sec, nanos));
                }
            }

            // Парсим metadata
            if (rootNode.has("metadata") && rootNode.get("metadata").isObject()) {
                var metadataNode = rootNode.get("metadata");
                Map<String, String> metadata = new HashMap<>();

                var fields = metadataNode.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    metadata.put(field.getKey(), field.getValue().asText());
                }

                message.setMetadata(metadata);
            }

            return message;
        }
    }

    private ElasticLogDocument createElasticDocument(KafkaLogMessage logMessage) {
        ElasticLogDocument doc = new ElasticLogDocument();
        doc.setId(logMessage.getId());
        doc.setTimestamp(logMessage.getTimestamp() != null ? logMessage.getTimestamp() : Instant.now());
        doc.setService(logMessage.getService());
        doc.setLevel(logMessage.getLevel());
        doc.setMessage(logMessage.getMessage());
        doc.setHost(logMessage.getHost());
        doc.setMetadata(logMessage.getMetadata());
        return doc;
    }

    private void saveFailedMessage(String jsonMessage, String error) {
        try {
            // Сохраняем failed message в Cassandra для дальнейшего анализа
            Map<String, String> metadata = new HashMap<>();
            metadata.put("error", error);
            metadata.put("failed_at", Instant.now().toString());
            metadata.put("original_message", jsonMessage.substring(0, Math.min(500, jsonMessage.length())));

            logDao.saveLog("kafka-dlq", "ERROR", "Failed to process Kafka message", "kafka-consumer", metadata);

            log.info("Saved failed message to DLQ");
        } catch (Exception e) {
            log.error("Even DLQ saving failed: {}", e.getMessage());
        }
    }

    // Вспомогательный метод для тестирования
    public void testConsumer(String testMessage) {
        log.info("TESTING CONSUMER with message: {}", testMessage);
        consumeRawLog(testMessage);
    }
}
