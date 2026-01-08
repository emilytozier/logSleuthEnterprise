
package com.example.logSleuthEnterprise.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

@Component
public class CassandraConnectionTest {

    private static final Logger log = LoggerFactory.getLogger(CassandraConnectionTest.class);

    @PostConstruct
    public void testConnection() {
        log.info("=== Testing Cassandra Connection ===");

        CqlSession session = null;
        try {
            // Пробуем подключиться
            session = CqlSession.builder()
                    .addContactPoint(new InetSocketAddress("localhost", 9042))
                    .withLocalDatacenter("datacenter1")
                    .build();

            // Выполняем простой запрос
            var result = session.execute("SELECT release_version FROM system.local");
            var row = result.one();

            if (row != null) {
                String version = row.getString("release_version");
                log.info("SUCCESS: Connected to Cassandra v{}", version);

                // Создаем keyspace
                createKeyspace(session);

            } else {
                log.error("Connected but no data returned");
            }

        } catch (Exception e) {
            log.error("FAILED to connect to Cassandra: {}", e.getMessage());
            log.debug("Error details:", e);
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    private void createKeyspace(CqlSession session) {
        try {
            String query = "CREATE KEYSPACE IF NOT EXISTS logsleuth_keyspace "
                    + "WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}";

            session.execute(query);
            log.info("Keyspace 'logsleuth_keyspace' created or already exists");

        } catch (Exception e) {
            log.error("Failed to create keyspace: {}", e.getMessage());
        }
    }
}
