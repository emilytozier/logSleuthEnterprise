// Файл: LogDao.java
package com.example.logSleuthEnterprise.dao;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.*;

@Repository
public class LogDAO {

    private static final Logger log = LoggerFactory.getLogger(LogDAO.class);
    private CqlSession session;

    @PostConstruct
    public void init() {
        log.info("=== Initializing LogDao ===");

        try {

            session = CqlSession.builder()
                    .addContactPoint(new InetSocketAddress("localhost", 9042))
                    .withLocalDatacenter("datacenter1")
                    .build();

            log.info("Cassandra session created");

            initializeDatabase();

        } catch (Exception e) {
            log.error("Failed to initialize LogDao: {}", e.getMessage());
            session = null;
        }
    }

    private void initializeDatabase() {
        try {

            String createKeyspace = "CREATE KEYSPACE IF NOT EXISTS logsleuth_keyspace "
                    + "WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1} "
                    + "AND durable_writes = true";

            session.execute(createKeyspace);
            log.info("Keyspace ready");


            session.execute("USE logsleuth_keyspace");


            String createTable = "CREATE TABLE IF NOT EXISTS logs ("
                    + "id uuid PRIMARY KEY, "
                    + "timestamp timestamp, "
                    + "service text, "
                    + "level text, "
                    + "message text, "
                    + "host text, "
                    + "metadata map<text, text>"
                    + ")";

            session.execute(createTable);
            log.info("Table 'logs' ready");

            try {
                String createIndex = "CREATE INDEX IF NOT EXISTS ON logs (service)";
                session.execute(createIndex);
                log.info("Index on 'service' ready");
            } catch (Exception e) {
                log.warn("Index might already exist: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("Database initialization failed: {}", e.getMessage());
        }
    }

    public Map<String, Object> saveLog(String service, String level, String message, String host, Map<String, String> metadata) {
        if (session == null) {
            log.warn("Cassandra session not available");
            return Map.of("error", "Database not available");
        }

        try {
            UUID id = UUID.randomUUID();
            Instant timestamp = Instant.now();

            String query = "INSERT INTO logs (id, timestamp, service, level, message, host, metadata) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

            PreparedStatement prepared = session.prepare(query);
            BoundStatement bound = prepared.bind(id, timestamp, service, level, message, host, metadata);
            session.execute(bound);

            log.debug("Log saved: {} - {}", service, level);

            Map<String, Object> savedLog = new HashMap<>();
            savedLog.put("id", id.toString());
            savedLog.put("timestamp", timestamp.toString());
            savedLog.put("service", service);
            savedLog.put("level", level);
            savedLog.put("message", message);
            savedLog.put("host", host);
            savedLog.put("metadata", metadata);

            return savedLog;

        } catch (Exception e) {
            log.error("Failed to save log: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }


    public List<Map<String, Object>> saveLogsBatch(List<Map<String, Object>> logs) {
        List<Map<String, Object>> savedLogs = new ArrayList<>();

        for (Map<String, Object> logEntry : logs) {
            String service = (String) logEntry.getOrDefault("service", "unknown");
            String level = (String) logEntry.getOrDefault("level", "INFO");
            String message = (String) logEntry.getOrDefault("message", "");
            String host = (String) logEntry.getOrDefault("host", "localhost");

            @SuppressWarnings("unchecked")
            Map<String, String> metadata = (Map<String, String>) logEntry.getOrDefault("metadata", new HashMap<>());

            Map<String, Object> saved = saveLog(service, level, message, host, metadata);
            savedLogs.add(saved);
        }

        return savedLogs;
    }

    public List<Map<String, Object>> getAllLogs(int limit) {
        List<Map<String, Object>> logs = new ArrayList<>();

        if (session == null) {
            return logs;
        }

        try {
            String query = "SELECT * FROM logs LIMIT ?";
            PreparedStatement prepared = session.prepare(query);
            BoundStatement bound = prepared.bind(limit);
            ResultSet result = session.execute(bound);

            for (Row row : result) {
                Map<String, Object> logEntry = mapRowToLog(row);
                logs.add(logEntry);
            }

            log.debug("Retrieved {} logs", logs.size());

        } catch (Exception e) {
            log.error("Failed to get logs: {}", e.getMessage());
        }

        return logs;
    }


    public List<Map<String, Object>> getLogsByService(String service, int limit) {
        List<Map<String, Object>> logs = new ArrayList<>();

        if (session == null) {
            return logs;
        }

        try {
            String query = "SELECT * FROM logs WHERE service = ? LIMIT ?";
            PreparedStatement prepared = session.prepare(query);
            BoundStatement bound = prepared.bind(service, limit);
            ResultSet result = session.execute(bound);

            for (Row row : result) {
                logs.add(mapRowToLog(row));
            }

        } catch (Exception e) {
            log.error("Failed to get logs by service: {}", e.getMessage());
        }

        return logs;
    }

    public List<Map<String, Object>> getLogsByLevel(String level, int limit) {
        List<Map<String, Object>> logs = new ArrayList<>();

        if (session == null) {
            return logs;
        }

        try {
            String query = "SELECT * FROM logs WHERE level = ? LIMIT ? ALLOW FILTERING";
            PreparedStatement prepared = session.prepare(query);
            BoundStatement bound = prepared.bind(level, limit);
            ResultSet result = session.execute(bound);

            for (Row row : result) {
                logs.add(mapRowToLog(row));
            }

        } catch (Exception e) {
            log.error("Failed to get logs by level: {}", e.getMessage());
        }

        return logs;
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();

        if (session == null) {
            return stats;
        }

        try {

            ResultSet countResult = session.execute("SELECT COUNT(*) FROM logs");
            Row countRow = countResult.one();
            long total = countRow != null ? countRow.getLong(0) : 0;
            stats.put("total", total);

            stats.put("byLevel", getCountByLevel());

            stats.put("byService", getCountByService());

            List<Map<String, Object>> recent = getAllLogs(1);
            if (!recent.isEmpty()) {
                stats.put("lastLog", recent.get(0));
            }

        } catch (Exception e) {
            log.error("Failed to get stats: {}", e.getMessage());
        }

        return stats;
    }

    private Map<String, Long> getCountByLevel() {
        Map<String, Long> counts = new HashMap<>();

        try {

            List<Map<String, Object>> allLogs = getAllLogs(1000);

            for (Map<String, Object> log : allLogs) {
                String level = (String) log.get("level");
                counts.put(level, counts.getOrDefault(level, 0L) + 1);
            }

        } catch (Exception e) {
            log.error("Failed to count by level: {}", e.getMessage());
        }

        return counts;
    }

    private Map<String, Long> getCountByService() {
        Map<String, Long> counts = new HashMap<>();

        try {
            List<Map<String, Object>> allLogs = getAllLogs(1000);

            for (Map<String, Object> log : allLogs) {
                String service = (String) log.get("service");
                counts.put(service, counts.getOrDefault(service, 0L) + 1);
            }

        } catch (Exception e) {
            log.error("Failed to count by service: {}", e.getMessage());
        }

        return counts;
    }

    // Очистить все логи
    public void clearAllLogs() {
        if (session == null) return;

        try {
            session.execute("TRUNCATE logs");
            log.info("All logs cleared");
        } catch (Exception e) {
            log.error("Failed to clear logs: {}", e.getMessage());
        }
    }


    private Map<String, Object> mapRowToLog(Row row) {
        Map<String, Object> logEntry = new HashMap<>();

        logEntry.put("id", row.getUuid("id").toString());
        logEntry.put("timestamp", row.getInstant("timestamp").toString());
        logEntry.put("service", row.getString("service"));
        logEntry.put("level", row.getString("level"));
        logEntry.put("message", row.getString("message"));
        logEntry.put("host", row.getString("host"));
        logEntry.put("metadata", row.getMap("metadata", String.class, String.class));

        return logEntry;
    }
}
